/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * {@link GenericApplicationListener}适配器，将事件的处理委派给{@link EventListener}注释的方法。
 * @since 4.2
 */
public class ApplicationListenerMethodAdapter implements GenericApplicationListener {

	private static final boolean reactiveStreamsPresent = ClassUtils.isPresent(
			"org.reactivestreams.Publisher", ApplicationListenerMethodAdapter.class.getClassLoader());


	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * bean名称
	 */
	private final String beanName;

	/**
	 * 被@EventListener注解 Method对象
	 */
	private final Method method;

	/**
	 * 目标方法，一般和method属性值相同，
	 * 当@EventListener作用于AOP代理类上，则targetMethod表示真正处理方法
	 */
	private final Method targetMethod;

	/**
	 * 唯一标识@EventListener来源的 Class 以及 method
	 */
	private final AnnotatedElementKey methodKey;

	/**
	 * 监听器支持的事件类型
	 *
	 * 1 优先从@EventListener注解classes属性获取监听器支持的事件类型 用ResolvableType类型来表示
	 *
	 *   例如
	 *   @EventListener({String.class, Integer.class})表示支持事件类型为String.class 或者 Integer.class
	 *
	 * 2 从method方法第一个参数获取事件，用ResolvableType类型来表示
	 *
	 *   如果方法的第一个参数T Class不是ApplicationEvent或者其子类。
	 *   在触发事件时内部会转换成PayloadApplicationEvent<T>
	 *
	 *   例如
	 *   @EventListener
	 *   public void handleString(String payload)
	 *   表示支持事件类型为 PayloadApplicationEvent<String>，PayloadApplicationEvent，PayloadApplicationEvent子类
	 *
	 *   如果方法的第一个参数T Class是ApplicationEvent或者其子类。
	 *   在触发事件时事件为其本身
	 *
	 *   表示支持事件类型为
	 *   @EventListener
	 *   public void handleRaw(ApplicationEvent event)
	 *   表示支持事件类型为 ApplicationEvent 事件
	 *   表示支持事件类型为 GenericTestEvent<String> extends ApplicationEvent 事件
	 *
	 *   表示支持事件类型为
	 *   @EventListener
	 *   public void handleGenericString(ApplicationListenerMethodAdapterTests.GenericTestEvent<String> event)
	 *   表示支持事件类型为 ApplicationListenerMethodAdapterTests.GenericTestEvent<String>
	 *
	 *
	 */
	private final List<ResolvableType> declaredEventTypes;

	/**
	 * 优先级
	 */
	private final int order;

	/**
	 * 应用程序上下文
	 */
	@Nullable
	private ApplicationContext applicationContext;

	/**
	 * 处理应用程序事件的SpEL表达式解析,这里主要处理condition表达式
	 */
	@Nullable
	private EventExpressionEvaluator evaluator;

	/**
	 * 标识@EventListener 表达式
	 */
	@Nullable
	private final String condition;


	/**
	 * 实例化
	 */
	public ApplicationListenerMethodAdapter(String beanName, Class<?> targetClass, Method method) {
		/**  设置beanName **/
		this.beanName = beanName;
		/** 获取method桥接方法 **/
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		/**  获取并设置targetMethod **/
		this.targetMethod = (!Proxy.isProxyClass(targetClass) ?
				AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
		/**  设置methodKey **/
		this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);

		/**  获取@EventListener 注解对象 **/
		EventListener ann = AnnotatedElementUtils.findMergedAnnotation(this.targetMethod, EventListener.class);

		/**  从method方法第一个参数或@EventListener注解classes属性中获取事件的ResolvableType类型**/
		this.declaredEventTypes = resolveDeclaredEventTypes(method, ann);

		/**  从@EventListener注解condition属性获取SpEL表达式**/
		this.condition = (ann != null ? ann.condition() : null);

		/**  从Order.class注解获取value属性，用来表示优先级别 **/
		this.order = resolveOrder(this.targetMethod);
	}

	/**
	 * 从method方法参数和@EventListener注解classes属性中获取事件的ResolvableType类型
	 */
	private static List<ResolvableType> resolveDeclaredEventTypes(Method method, @Nullable EventListener ann) {

		/** 1 @EventListener注解定义的方法参数只能一个 **/
		int count = method.getParameterCount();
		if (count > 1) {
			throw new IllegalStateException(
					"Maximum one parameter is allowed for event listener method: " + method);
		}

		/** 2 优先从@EventListener注解classes属性中获取事件的ResolvableType类型 **/
		if (ann != null) {
			Class<?>[] classes = ann.classes();
			if (classes.length > 0) {
				List<ResolvableType> types = new ArrayList<>(classes.length);
				for (Class<?> eventType : classes) {
					types.add(ResolvableType.forClass(eventType));
				}
				return types;
			}
		}

		/** 3 从method方法第一个参数获取事件的ResolvableType类型 **/
		if (count == 0) {
			throw new IllegalStateException(
					"Event parameter is mandatory for event listener method: " + method);
		}
		return Collections.singletonList(ResolvableType.forMethodParameter(method, 0));
	}

	/**
	 * 从Order.class注解获取value属性，用来表示优先级别
	 */
	private static int resolveOrder(Method method) {
		Order ann = AnnotatedElementUtils.findMergedAnnotation(method, Order.class);
		return (ann != null ? ann.value() : 0);
	}


	/**
	 * 初始化ApplicationListenerMethodAdapter，设置applicationContext，evaluator
	 */
	void init(ApplicationContext applicationContext, EventExpressionEvaluator evaluator) {
		this.applicationContext = applicationContext;
		this.evaluator = evaluator;
	}




	/**
	 * 确定此侦听器是否实际支持给定的事件,事件类型用 ResolvableType 表示
	 */
	@Override
	public boolean supportsEventType(ResolvableType eventType) {
		for (ResolvableType declaredEventType : this.declaredEventTypes) {
			/**
			 * 如果declaredEventType 表示类型为 ApplicationEvent
			 *
			 * 例如
			 *  @EventListener
			 * 	public void handleRaw(ApplicationEvent event)
			 *
			 * **/
			if (declaredEventType.isAssignableFrom(eventType)) {
				return true;
			}
			/**
			 * 如果declaredEventType 表示类型为 非ApplicationEvent
			 *
			 * 例如
			 * @EventListener({String.class, Integer.class})
			 * 或者
			 * @EventListener
			 * public void handleRaw(ApplicationEvent event)
			 *
			 * 从触发事件定义的泛型来匹配
			 *
			 * **/
			if (PayloadApplicationEvent.class.isAssignableFrom(eventType.toClass())) {
				ResolvableType payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric();
				if (declaredEventType.isAssignableFrom(payloadType)) {
					return true;
				}
			}
		}
		/** 判断触发事件是否存在泛型  **/
		return eventType.hasUnresolvableGenerics();
	}

	/**
	 * 确定此侦听器是否实际支持给定的源类型
	 */
	@Override
	public boolean supportsSourceType(@Nullable Class<?> sourceType) {
		return true;
	}

	/**
	 * 返回监听器的优先级
	 */
	@Override
	public int getOrder() {
		return this.order;
	}


	/**
	 * 处理 ApplicationEvent 事件。
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		processEvent(event);
	}


	/**
	 * 处理事件
	 */
	public void processEvent(ApplicationEvent event) {
		/** 解析ApplicationEvent，获取处理方法的参数 **/
		Object[] args = resolveArguments(event);
		/** 判断是否满足 condition 表达式 **/
		if (shouldHandle(event, args)) {
			/** 使用给定的参数值调用事件侦听器方法。 **/
			Object result = doInvoke(args);
			if (result != null) {
				handleResult(result);
			}
			else {
				logger.trace("No result object given - no result to handle");
			}
		}
	}

	/**
	 * 解析ApplicationEvent， 获取处理方法的参数
	 *
	 * 对于事件类型为 PayloadApplicationEvent 事件，获取负载事件对象
	 * 对于事件类型为 ApplicationEvent 事件 返回本身
	 *
	 */
	@Nullable
	protected Object[] resolveArguments(ApplicationEvent event) {
		/**
		 * 解析事件或是ApplicationEvent 事件 ResolvableType 类型
		 * 对于事件类型为 PayloadApplicationEvent<E> 事件 返回泛型E对应 ResolvableType 类型
		 * 对于事件类型为 ApplicationEvent 事件 返回ApplicationEventd对象对应  ResolvableType 类型
		 * **/
		ResolvableType declaredEventType = getResolvableType(event);
		if (declaredEventType == null) {
			return null;
		}
		if (this.method.getParameterCount() == 0) {
			return new Object[0];
		}
		Class<?> declaredEventClass = declaredEventType.toClass();
		if (!ApplicationEvent.class.isAssignableFrom(declaredEventClass) &&
				event instanceof PayloadApplicationEvent) {
			Object payload = ((PayloadApplicationEvent<?>) event).getPayload();
			if (declaredEventClass.isInstance(payload)) {
				return new Object[] {payload};
			}
		}
		return new Object[] {event};
	}

	protected void handleResult(Object result) {
		if (reactiveStreamsPresent && new ReactiveResultHandler().subscribeToPublisher(result)) {
			if (logger.isTraceEnabled()) {
				logger.trace("Adapted to reactive result: " + result);
			}
		}
		else if (result instanceof CompletionStage) {
			((CompletionStage<?>) result).whenComplete((event, ex) -> {
				if (ex != null) {
					handleAsyncError(ex);
				}
				else if (event != null) {
					publishEvent(event);
				}
			});
		}
		else if (result instanceof ListenableFuture) {
			((ListenableFuture<?>) result).addCallback(this::publishEvents, this::handleAsyncError);
		}
		else {
			publishEvents(result);
		}
	}

	private void publishEvents(Object result) {
		if (result.getClass().isArray()) {
			Object[] events = ObjectUtils.toObjectArray(result);
			for (Object event : events) {
				publishEvent(event);
			}
		}
		else if (result instanceof Collection<?>) {
			Collection<?> events = (Collection<?>) result;
			for (Object event : events) {
				publishEvent(event);
			}
		}
		else {
			publishEvent(result);
		}
	}

	private void publishEvent(@Nullable Object event) {
		if (event != null) {
			Assert.notNull(this.applicationContext, "ApplicationContext must not be null");
			this.applicationContext.publishEvent(event);
		}
	}

	protected void handleAsyncError(Throwable t) {
		logger.error("Unexpected error occurred in asynchronous listener", t);
	}

	private boolean shouldHandle(ApplicationEvent event, @Nullable Object[] args) {
		if (args == null) {
			return false;
		}
		String condition = getCondition();
		if (StringUtils.hasText(condition)) {
			Assert.notNull(this.evaluator, "EventExpressionEvaluator must not be null");
			return this.evaluator.condition(
					condition, event, this.targetMethod, this.methodKey, args, this.applicationContext);
		}
		return true;
	}

	/**
	 * 使用给定的参数值调用事件侦听器方法。
	 */
	@Nullable
	protected Object doInvoke(Object... args) {
		Object bean = getTargetBean();
		ReflectionUtils.makeAccessible(this.method);
		try {
			return this.method.invoke(bean, args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(this.method, bean, args);
			throw new IllegalStateException(getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new IllegalStateException(getInvocationErrorMessage(bean, ex.getMessage(), args), ex);
		}
		catch (InvocationTargetException ex) {
			// Throw underlying exception
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else {
				String msg = getInvocationErrorMessage(bean, "Failed to invoke event listener method", args);
				throw new UndeclaredThrowableException(targetException, msg);
			}
		}
	}

	/**
	 * Return the target bean instance to use.
	 */
	protected Object getTargetBean() {
		Assert.notNull(this.applicationContext, "ApplicationContext must no be null");
		return this.applicationContext.getBean(this.beanName);
	}

	/**
	 * Return the condition to use.
	 * <p>Matches the {@code condition} attribute of the {@link EventListener}
	 * annotation or any matching attribute on a composed annotation that
	 * is meta-annotated with {@code @EventListener}.
	 */
	@Nullable
	protected String getCondition() {
		return this.condition;
	}

	/**
	 * Add additional details such as the bean type and method signature to
	 * the given error message.
	 * @param message error message to append the HandlerMethod details to
	 */
	protected String getDetailedErrorMessage(Object bean, String message) {
		StringBuilder sb = new StringBuilder(message).append("\n");
		sb.append("HandlerMethod details: \n");
		sb.append("Bean [").append(bean.getClass().getName()).append("]\n");
		sb.append("Method [").append(this.method.toGenericString()).append("]\n");
		return sb.toString();
	}

	/**
	 * Assert that the target bean class is an instance of the class where the given
	 * method is declared. In some cases the actual bean instance at event-
	 * processing time may be a JDK dynamic proxy (lazy initialization, prototype
	 * beans, and others). Event listener beans that require proxying should prefer
	 * class-based proxy mechanisms.
	 */
	private void assertTargetBean(Method method, Object targetBean, Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String msg = "The event listener method class '" + methodDeclaringClass.getName() +
					"' is not an instance of the actual bean class '" +
					targetBeanClass.getName() + "'. If the bean requires proxying " +
					"(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(targetBean, msg, args));
		}
	}

	private String getInvocationErrorMessage(Object bean, String message, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(bean, message));
		sb.append("Resolved arguments: \n");
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}

	/**
	 * 解析事件或是ApplicationEvent 事件 ResolvableType 类型
	 */
	@Nullable
	private ResolvableType getResolvableType(ApplicationEvent event) {
		ResolvableType payloadType = null;
		if (event instanceof PayloadApplicationEvent) {
			PayloadApplicationEvent<?> payloadEvent = (PayloadApplicationEvent<?>) event;
			ResolvableType eventType = payloadEvent.getResolvableType();
			if (eventType != null) {
				payloadType = eventType.as(PayloadApplicationEvent.class).getGeneric();
			}
		}
		for (ResolvableType declaredEventType : this.declaredEventTypes) {
			Class<?> eventClass = declaredEventType.toClass();
			if (!ApplicationEvent.class.isAssignableFrom(eventClass) &&
					payloadType != null && declaredEventType.isAssignableFrom(payloadType)) {
				return declaredEventType;
			}
			if (eventClass.isInstance(event)) {
				return declaredEventType;
			}
		}
		return null;
	}


	@Override
	public String toString() {
		return this.method.toGenericString();
	}


	private class ReactiveResultHandler {

		public boolean subscribeToPublisher(Object result) {
			ReactiveAdapter adapter = ReactiveAdapterRegistry.getSharedInstance().getAdapter(result.getClass());
			if (adapter != null) {
				adapter.toPublisher(result).subscribe(new EventPublicationSubscriber());
				return true;
			}
			return false;
		}
	}


	private class EventPublicationSubscriber implements Subscriber<Object> {

		@Override
		public void onSubscribe(Subscription s) {
			s.request(Integer.MAX_VALUE);
		}

		@Override
		public void onNext(Object o) {
			publishEvents(o);
		}

		@Override
		public void onError(Throwable t) {
			handleAsyncError(t);
		}

		@Override
		public void onComplete() {
		}
	}

}
