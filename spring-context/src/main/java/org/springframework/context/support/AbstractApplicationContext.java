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

package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since January 21, 2001
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * BeanFactory中MessageSource bean的名称
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * BeanFactory中LifecycleProcessor bean的名称
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	/**
	 * BeanFactory中ApplicationEventMulticaster bean的名称
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";


	static {
		ContextClosedEvent.class.getName();
	}


	/** 日志组件 */
	protected final Log logger = LogFactory.getLog(getClass());

	/** 唯一ID. */
	private String id = ObjectUtils.identityToString(this);

	/** 描述名称 */
	private String displayName = ObjectUtils.identityToString(this);

	/** 父类ApplicationContext */
	@Nullable
	private ApplicationContext parent;

	/**  环境配置组件 Environment */
	@Nullable
	private ConfigurableEnvironment environment;

	/** BeanFactoryPostProcessors 列表，在ApplicationContext刷新时。*/
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/** 此ApplicationContext刷新启动时的系统时间（以毫秒为单位）。*/
	private long startupDate;

	/** 指示此ApplicationContext当前是否处于活动状态的标志。*/
	private final AtomicBoolean active = new AtomicBoolean();

	/** 指示此ApplicationContext是否已经关闭的标志。*/
	private final AtomicBoolean closed = new AtomicBoolean();

	/** 同步监视器锁对象，用于的“刷新”和“销毁”*/
	private final Object startupShutdownMonitor = new Object();

	/** 注册到JVM钩子线程 */
	@Nullable
	private Thread shutdownHook;

	/** 资源模式解析器组件 */
	private ResourcePatternResolver resourcePatternResolver;

	/** 生命周期处理器组件。*/
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/** 国际化处理器组件. */
	@Nullable
	private MessageSource messageSource;

	/** 应用事件多播器组件 */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/** 事件监听器 */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/** 存储刷新过程中处理延时发布的事件监听器，这里一般是初始化过程中某个时刻applicationListeners的克隆 */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/** 刷新过程中触发事件延时发布的事件。 */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * 实例化一个新的AbstractApplicationContext，内部默认会设置资源模式解析器
	 */
	public AbstractApplicationContext() {
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * 实例化一个新的AbstractApplicationContext，设置其父AbstractApplicationContext，内部默认会设置资源模式解析器
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// ApplicationContext接口的实现
	//---------------------------------------------------------------------


	/**
	 * 设置唯一ID
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * 获取唯一ID
	 */
	@Override
	public String getId() {
		return this.id;
	}

	/**
	 * 获取应用名称
	 */
	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * 设置描述名称
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * 获取描述名称
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * 获取父ApplicationContext
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * 设置环境配置组件environment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * 返回环境配置组件environment，如果不存在则新建一个
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * 创建并返回一个新的{@link StandardEnvironment}。
	 * 子类可以重写此方法以提供自定义{@link ConfigurableEnvironment}实现。
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * 获取ApplicationContext的内部BeanFactory,如果存在，则将其返回为AutowireCapableBeanFactory。
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * 返回首次刷新ApplicationContext时的时间戳。
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * 将指定事件发布给所有注册到当前ApplicationContext所有监听器
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * 将指定事件发布给所有注册到当前ApplicationContext所有监听器。并指定事件类型
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * 将指定事件发布给所有注册到当前ApplicationContext所有监听器。并指定事件类型
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		/** 校验发布事件对象不能为null **/
		Assert.notNull(event, "Event must not be null");

		/** 记录要发布的事件 ApplicationEvent**/
		ApplicationEvent applicationEvent;

		/** 事件类型是ApplicationEvent **/
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		/** 事件类型不是ApplicationEvent **/
		else {
			/** 将事件对象封装成PayloadApplicationEvent **/
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		/** 如果触发事件时正在进行刷新需要延迟触发事件。将其存入earlyApplicationEvents，换句话说如果earlyApplicationEvents不为null说明当前正在刷新动作
		 * 在刷新refresh().prepareRefresh()时对earlyApplicationEvents列表初始化，此时发布事件都会放入earlyApplicationEvents列表
		 * 在刷新refresh().registerListeners时将earlyApplicationEvents列表中事件发布给所有侦听器，同时重写置空earlyApplicationEvents.**/
		if (this.earlyApplicationEvents != null) {
			this.earlyApplicationEvents.add(applicationEvent);
		}
		/** 获取应用事件多播器组件，发布事件通知所有监听器处理 **/
		else {
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		/** 如果存在父ApplicationContext， 将指定事件发布给所有注册到父ApplicationContext所有监听器。并指定事件类型 **/
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			}
			else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * 返回应用事件多播器组件
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	/**
	 * 返回生命周期处理器组件。
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * 返回资源模式解析器，默认实现 PathMatchingResourcePatternResolver
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// ConfigurableApplicationContext接口的实现
	//---------------------------------------------------------------------

	/**
	 * 设置父ApplicationContext
	 * 这里会将父ApplicationContext中Environment
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	/**
	 * 注册BeanFactoryPostProcessor，
	 * BeanFactoryPostProcessor在刷新执行postProcessBeanFactory时被调用，对BeanFactory功能做扩展
	 */
	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * 获取注册所有BeanFactoryPostProcessor列表
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	/**
	 * 注册触发ApplicationEvent事件监听器ApplicationListener
	 */
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	/**
	 * 返回所有注册触发ApplicationEvent事件监听器ApplicationListener
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	/**
	 * 刷新
	 */
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {

			/**  准备刷新 **/
			prepareRefresh();

			/**  获取新鲜 BeanFactory  **/
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			/** BeanFactory做预处理 **/
			prepareBeanFactory(beanFactory);

			try {
				/** 模板方法，默认实现为空，子类实现扩展beanFactory功能 **/
				postProcessBeanFactory(beanFactory);

				/**  执行beanFactory中管理BeanFactoryPostProcessor对象postProcessBeanFactory方法，**/
				invokeBeanFactoryPostProcessors(beanFactory);

				/** 将BeanPostProcessor按照顺序注册到BeanFactory **/
				registerBeanPostProcessors(beanFactory);

				/** 初始化 MessageSource **/
				initMessageSource();

				/** 初始化 ApplicationEventMulticaster. **/
				initApplicationEventMulticaster();

				/** 模板方法,空实现，子类扩展以添加特定于上下文的刷新工作。 **/
				onRefresh();

				/** 检查侦听器bean并注册它们。 **/
				registerListeners();

				/** 完成此上下文的bean工厂的初始化，初始化所有剩余的单例bean。 **/
				finishBeanFactoryInitialization(beanFactory);

				/** 完成此上下文的刷新 **/
				finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				/**   销毁ApplicationContext管理所有Bean实例，默认实现销毁此上下文中所有缓存的单例 **/
				destroyBeans();

				/** 取消applicationContext刷新功能 **/
				cancelRefresh(ex);

				/** 抛出异常 **/
				throw ex;
			}

			finally {
				/** 重置Spring的通用反射元数据缓存 **/
				resetCommonCaches();
			}
		}
	}

	/**
	 * 准备刷新
	 */
	protected void prepareRefresh() {
		/**  设置启动时间  **/
		this.startupDate = System.currentTimeMillis();
		/** 设置ApplicationContext 关闭状态为false **/
		this.closed.set(false);
		/** 设置ApplicationContext 激活状态为true **/
		this.active.set(true);


		/** 打印刷新日志 **/
		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			}
			else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		/** 模板方法 用来初始化PropertySources。子类通常在此新建Environment**/
		initPropertySources();

		/** 验证环境配置组件environment所有的必须的属性  **/
		getEnvironment().validateRequiredProperties();

		/** 使用applicationListeners初始化earlyApplicationListeners，处理earlyApplicationEvents中延时事件**/
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		/** 初始化earlyApplicationEvents，表示当前ApplicationContext容器正在刷新，如果发布事件不会被处理，放入earlyApplicationEvents集合中延时处理 **/
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}


	/**
	 * 模板方法 用来初始化PropertySources。
	 * AbstractRefreshableWebApplicationContext重写当前方法，调用 getEnvironment()新建Environment，
	 * 如果新建Environment类型ConfigurableWebEnvironment则调用initPropertySources初始化
	 */
	protected void initPropertySources() {
	}

	/**
	 * 获取新鲜 BeanFactory
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		/** 模板方法，子类实现，刷新内部 BeanFactory，2个子类对此做了实现 **/
		/**  AbstractRefreshableApplicationContext 销毁已存在BeanFactory，同时创建新的BeanFactory**/
		/**  GenericApplicationContext不执行任何操作：实例化时就创建内部BeanFactory， **/
		refreshBeanFactory();
		/** 模板方法，返回内部 BeanFactory 2个子类对此做了实现**/
		/**  AbstractRefreshableApplicationContext 返回内部BeanFactory 本质返回DefaultListableBeanFactory**/
		/**  GenericApplicationContext不执行任何操作：返回内部ConfigurableListableBeanFactory,本质返回DefaultListableBeanFactory **/
		return getBeanFactory();
	}

	/**
	 * BeanFactory做预处理
	 */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/**  设置beanFactory的classLoader **/
		beanFactory.setBeanClassLoader(getClassLoader());
		/**  设置beanFactory的表达式语言处理器,Spring3开始增加了对语言表达式的支持,默认可以使用#{bean.xxx}的形式来调用相关属性值 **/
		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		/** 为beanFactory增加一个默认的propertyEditor **/
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));
		/** 注册BeanPostProcessor 实现类ApplicationContextAwareProcessor给beanFactory **/
		/** ApplicationContextAwareProcessor负责在初始化Bean之前解析Bean类型，调用Aware接口方法 **/
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

		/** 设置忽略自动装配的接口 **/
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		/** 设置注入规则：如果注入类型为BeanFactory时，注入beanFactory(DefaultListableBeanFactory)  **/
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		/** 设置注入规则：注入类型为ApplicationEventPublisher，ApplicationEventPublisher，ApplicationContext时，注入当前对象this(ApplicationContext) **/
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		/** 注册BeanPostProcessor 接口实现类ApplicationListenerDetector **/
		/** 负责将类型为ApplicationListener bean对象作为ApplicationListener监听器注册到ApplicationContext **/
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		/** 如果内部beanFactory中存在loadTimeWeaver，则
		 * 1 给beanFactory注册BeanPostProcessor接口实现LoadTimeWeaverAwareProcessor，
		 * 2 设置ContextTypeMatchClassLoader作为beanFactory TempClassLoader**/
		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		/** 将System.getProperties() 作为单例 bean 对象，已名称 systemProperties 注册到beanFactory **/
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		/** 将System.getProperties() 作为单例 bean 对象，已名称 systemProperties 注册到beanFactory **/
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		/** 将System.getenv() 作为单例 bean 对象，已名称 systemEnvironment 注册到beanFactory **/
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

	/**
	 * 模板方法，默认实现为空，子类实现扩展beanFactory功能
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
	}

	/**
	 * 执行beanFactory中管理BeanFactoryPostProcessor对象postProcessBeanFactory方法，这里管理的BeanFactoryPostProcessor包括
	 * 		  1 手动注册到ApplicationContext
	 * 		  2 beanFactory内部管理类型为BeanFactoryPostProcessor bean对象
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		/**
		 * 执行beanFactory中管理BeanFactoryPostProcessor对象postProcessBeanFactory方法，这里管理的BeanFactoryPostProcessor包括
		 *  1 手动注册到ApplicationContext
		 *  2 beanFactory内部管理类型为BeanFactoryPostProcessor bean对象
		 * **/
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		/** 如果beanFactory中存在loadTimeWeaver，则
		 * 1 给beanFactory注册BeanPostProcessor接口实现LoadTimeWeaverAwareProcessor，
		 * 2 设置ContextTypeMatchClassLoader作为beanFactory TempClassLoader**/
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * 将BeanPostProcessor按照顺序注册到BeanFactory,其中包括
	 * 1 BeanPostProcessorChecker,
	 * 2 从beanFactory获取类型为BeanPostProcessor bean实例
	 * 3 ApplicationListenerDetector
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * 初始化 MessageSource.
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * 初始化 ApplicationEventMulticaster.
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * 初始化 LifecycleProcessor.
	 * 如果上下文中未定义，则使用DefaultLifecycleProcessor。
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor =
					beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		}
		else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * 模板方法,空实现，子类扩展以添加特定于上下文的刷新工作。 ,
	 */
	protected void onRefresh() throws BeansException {
	}

	/**
	 * 检查侦听器bean并注册它们。
	 */
	protected void registerListeners() {
		/** 将设置静态指定的侦听器注册到事件多播器 */
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		/** 获取Bean中类型为ApplicationListener Bean实例 注册到事件多播器 **/
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		/** 使用多播器，发布刷新前触发的应用事件。 **/
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (earlyEventsToProcess != null) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * 完成此上下文的bean工厂的初始化，初始化所有剩余的单例bean。
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		/** 为此applicationContext初始ConversionService。 **/
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		/** 如果没有bean后处理器，则注册默认的嵌入式值解析器 **/
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		/** 尽早初始化LoadTimeWeaverAware Bean，以便尽早注册其转换器 **/
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		/** 停止使用临时的ClassLoader进行类型匹配。 **/
		beanFactory.setTempClassLoader(null);

		/** 允许缓存所有bean定义元数据，而不期望进一步的更改。 **/
		beanFactory.freezeConfiguration();

		/** 实例化所有（非延迟初始化）单例Bean **/
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * 完成此上下文的刷新，
	 */
	protected void finishRefresh() {
		/** 清除applicationContext级别的资源缓存 **/
		clearResourceCaches();

		/** 初始化 LifecycleProcessor. **/
		initLifecycleProcessor();

		/** 刷新传播到生命周期处理器。 **/
		getLifecycleProcessor().onRefresh();

		/**  发布ContextRefreshedEvent事件 **/
		publishEvent(new ContextRefreshedEvent(this));

		/**  如果活动，请参加LiveBeansView MBean。**/
		LiveBeansView.registerApplicationContext(this);
	}

	/**
	 * 取消applicationContext刷新功能
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * 重置Spring的通用反射元数据缓存，尤其是
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * @since 4.2
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 */
	protected void resetCommonCaches() {
		ReflectionUtils.clearCache();
		AnnotationUtils.clearCache();
		ResolvableType.clearCache();
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/**
	 * 注册一个关闭钩子线程名称为（SpringContextShutdownHook），在JVM关闭时被触发，调用{@code doClose()}执行实际关闭操作
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}


	/**
	 * 销毁
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * 关闭 applicationContext,
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			/** 向JVM注销钩子线程shutdownHook **/
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * 实际执行上下文关闭：发布ContextClosedEvent并销毁此应用程序上下文的bean工厂中的单例。 , * <p>由{@code close（）}和JVM关闭钩子（如果有）调用。
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		/** 检查是否需要实际尝试关闭... **/
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			LiveBeansView.unregisterApplicationContext(this);

			/**  发布 ContextStoppedEvent 事件**/
			try {
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			/** 生命周期处理器组件执行停止，停止所有Lifecycle bean **/
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			/** 销毁ApplicationContext管理所有Bean实例，默认实现销毁此上下文中所有缓存的单例 **/
			destroyBeans();

			/** 模板方法，关闭内部 BeanFactory **/
			/** AbstractRefreshableApplicationContext重写当前方法，将内部BeanFactory设置为null**/
			/** GenericApplicationContext重写当前方法，并未对内部BeanFactory做处理**/
			closeBeanFactory();

			/** 模板方法，默认实现为空，让子类做一些最后的清理 **/
			onClose();

			/** 重置applicationListeners **/
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			/** 修改活动状态为关闭 **/
			this.active.set(false);
		}
	}

	/**
	 * 销毁ApplicationContext管理所有Bean实例，默认实现销毁此上下文中所有缓存的单例
	 * 如果Bean类型为DisposableBean则调用{@code disposablebean.destroy（）}
	 * 如果beanDefinition指定的DestroyMethodName则调用指定销毁方法
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	/**
	 * 返回当前是否处于活动状态
	 */
	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * 断言此上下文的BeanFactory当前处于活动状态
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			}
			else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	//---------------------------------------------------------------------
	// BeanFactory接口的实现
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// ListableBeanFactory接口的实现
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}


	//---------------------------------------------------------------------
	// HierarchicalBeanFactory接口的实现
	//---------------------------------------------------------------------

	/**
	 * 返回内部BeanFactory的父类
	 * 这里直接返回父类ApplicationContext（ApplicationContext同样实现BeanFactory接口）
	 */
	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	/**
	 * 内部BeanFactory是否包含指定名称Bean
	 */
	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * 返回内部BeanFactory的父类
	 * 如果父类ApplicationContext类型为ConfigurableApplicationContext，会返回其内部内部BeanFactory，
	 * 否则返回和getParentBeanFactory()一样返回父类ApplicationContext本身。
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	//---------------------------------------------------------------------
	// MessageSource接口的实现
	//---------------------------------------------------------------------

	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}


	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}


	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	//---------------------------------------------------------------------
	// ResourcePatternResolver接口的实现
	//---------------------------------------------------------------------

	/**
	 * 将指定资源位置路径解析为一个或多个匹配资源
	 */
	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// 生命周期接口的实现
	//---------------------------------------------------------------------

	/**
	 * 启动
	 */
	@Override
	public void start() {
		getLifecycleProcessor().start();
		/**  发布 ContextStartedEvent 事件**/
		publishEvent(new ContextStartedEvent(this));
	}

	/**
	 * 停止
	 */
	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		/**  发布 ContextStoppedEvent 事件**/
		publishEvent(new ContextStoppedEvent(this));
	}

	/**
	 * 是否正在允许
	 */
	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// 子类必须实现的抽象方法
	//---------------------------------------------------------------------

	/**
	 * 模板方法，刷新（创建）内部 BeanFactory
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * 模板方法，关闭内部 BeanFactory
	 */
	protected abstract void closeBeanFactory();

	/**
	 * 模板方法，返回内部 BeanFactory
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;



	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
