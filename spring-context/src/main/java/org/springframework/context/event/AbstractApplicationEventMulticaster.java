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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * {@link ApplicationEventMulticaster}接口的抽象实现，提供基本的侦听器注册功能。
 */
public abstract class AbstractApplicationEventMulticaster
		implements ApplicationEventMulticaster, BeanClassLoaderAware, BeanFactoryAware {

	/**
	 * 封装事件广播器注册所有的 ApplicationListener
	 *
	 * 其内部通过 以下两个属性保存注册ApplicationListener对象和类型为ApplicationListener.class对应bean名称
	 * public final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
	 * public final Set<String> applicationListenerBeans = new LinkedHashSet<>();
	 */
	private final ListenerRetriever defaultRetriever = new ListenerRetriever(false);

	/**
	 * 基于事件类型和源类型 ListenerCacheKey 键 映射ListenerRetriever，
	 * ListenerRetriever为Helper类，它封装一组特定的目标侦听器，
	 */
	final Map<ListenerCacheKey, ListenerRetriever> retrieverCache = new ConcurrentHashMap<>(64);

	/**
	 * 加载Bean对象ClassLoader
	 */
	@Nullable
	private ClassLoader beanClassLoader;

	/**
	 * BeanFactory
	 */
	@Nullable
	private ConfigurableBeanFactory beanFactory;

	/**
	 * 单例bean注册表 （用来作为同步锁）
	 */
	private Object retrievalMutex = this.defaultRetriever;


	/**
	 * 设置加载Bean ClassLoader
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	/**
	 * 设置BeanFactory
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		if (this.beanClassLoader == null) {
			this.beanClassLoader = this.beanFactory.getBeanClassLoader();
		}
		this.retrievalMutex = this.beanFactory.getSingletonMutex();
	}

	/**
	 * 获取BeanFactory
	 */
	private ConfigurableBeanFactory getBeanFactory() {
		if (this.beanFactory == null) {
			throw new IllegalStateException("ApplicationEventMulticaster cannot retrieve listener beans " +
					"because it is not associated with a BeanFactory");
		}
		return this.beanFactory;
	}


	/**
	 * 注册监听器{@link ApplicationListener}对象
	 */
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		//同步锁
		synchronized (this.retrievalMutex) {
			// 如果注册ApplicationListener 是一个代理对象获取目标对象，如果类型同为ApplicationListener，从从defaultRetriever中删除
			Object singletonTarget = AopProxyUtils.getSingletonTarget(listener);
			if (singletonTarget instanceof ApplicationListener) {
				this.defaultRetriever.applicationListeners.remove(singletonTarget);
			}
			// 将注册监听器ApplicationListener对象 添加到defaultRetriever
			this.defaultRetriever.applicationListeners.add(listener);
			// 清理缓存
			this.retrieverCache.clear();
		}
	}

	/**
	 * 注册类型为ApplicationListener.class对应bean名称
	 */
	@Override
	public void addApplicationListenerBean(String listenerBeanName) {
		//同步锁
		synchronized (this.retrievalMutex) {
			// 将类型为ApplicationListener.class bean（名称） 添加到defaultRetriever
			this.defaultRetriever.applicationListenerBeans.add(listenerBeanName);
			// 清理缓存
			this.retrieverCache.clear();
		}
	}

	/**
	 * 删除监听器{@link ApplicationListener}对象
	 */
	@Override
	public void removeApplicationListener(ApplicationListener<?> listener) {
		//同步锁
		synchronized (this.retrievalMutex) {
			// 从defaultRetriever 删除指定ApplicationListener 对象监听器
			this.defaultRetriever.applicationListeners.remove(listener);
			// 清理缓存
			this.retrieverCache.clear();
		}
	}

	/**
	 * 删除类型为ApplicationListener.class bean(名称)
	 */
	@Override
	public void removeApplicationListenerBean(String listenerBeanName) {
		//同步锁
		synchronized (this.retrievalMutex) {
			// 从defaultRetriever 删除类型为ApplicationListener.class bean(名称)
			this.defaultRetriever.applicationListenerBeans.remove(listenerBeanName);
			// 清理缓存
			this.retrieverCache.clear();
		}
	}

	/**
	 * 删除所有{@link ApplicationListener}
	 */
	@Override
	public void removeAllListeners() {
		synchronized (this.retrievalMutex) {
			// 从defaultRetriever 删除所有注册ApplicationListener对象
			this.defaultRetriever.applicationListeners.clear();
			// 从defaultRetriever 删除所有注册类型为ApplicationListener.class bean(名称)
			this.defaultRetriever.applicationListenerBeans.clear();
			// 清理缓存
			this.retrieverCache.clear();
		}
	}


	/**
	 * 返回注册到广播器所有监听器
	 */
	protected Collection<ApplicationListener<?>> getApplicationListeners() {
		synchronized (this.retrievalMutex) {
			return this.defaultRetriever.getApplicationListeners();
		}
	}

	/**
	 * 返回与给定的事件类型。
	 * @param event 要传播的事件
·	 * @param eventType 事件类型
	 * @return  ApplicationListeners 集合
	 * @see org.springframework.context.ApplicationListener
	 */
	protected Collection<ApplicationListener<?>> getApplicationListeners(
			ApplicationEvent event, ResolvableType eventType) {

		/** 1 从缓存中获取ApplicationListener **/
		//获取事件中的事件源对象
		Object source = event.getSource();
		//获取事件源Class类型
		Class<?> sourceType = (source != null ? source.getClass() : null);

		//以(eventType, sourceType)事件类型和事件源类型为参数构建一个cacheKey
		ListenerCacheKey cacheKey = new ListenerCacheKey(eventType, sourceType);
		//从缓存retrieverCache中获取cacheKey对应ListenerRetriever
		//ListenerRetriever是一个Helper类，它封装一组支持特定条件(eventType, sourceType)的目标侦听器
		ListenerRetriever retriever = this.retrieverCache.get(cacheKey);
		if (retriever != null) {
			return retriever.getApplicationListeners();
		}

		/** 2 缓存中不存在处理逻辑 **/
		if (this.beanClassLoader == null ||
				(ClassUtils.isCacheSafe(event.getClass(), this.beanClassLoader) &&
						(sourceType == null || ClassUtils.isCacheSafe(sourceType, this.beanClassLoader)))) {
			// 同步锁
			synchronized (this.retrievalMutex) {
				// 2.1 double check 从缓存中获取ListenerRetriever
				retriever = this.retrieverCache.get(cacheKey);
				if (retriever != null) {
					return retriever.getApplicationListeners();
				}
                // 2.2 构建新ListenerRetriever，
				retriever = new ListenerRetriever(true);
				// 2.3 匹配支持特定条件(eventType, sourceType)ApplicationListener 设置到ListenerRetriever，并返回
				Collection<ApplicationListener<?>> listeners =
						retrieveApplicationListeners(eventType, sourceType, retriever);
				// 2.4 将ListenerRetriever 添加到缓存
				this.retrieverCache.put(cacheKey, retriever);
				// 2.5 返回支持特定条件(eventType, sourceType)ApplicationListener
				return listeners;
			}
		}
		else {
			// 匹配支持特定条件(eventType, sourceType)ApplicationListener，并返回
			return retrieveApplicationListeners(eventType, sourceType, null);
		}
	}

	/**
	 * 匹配支持特定条件(eventType, sourceType)ApplicationListener 设置到ListenerRetriever，并返回
	 * @param eventType 事件类型
	 * @param sourceType 事件来源
	 * @param retriever 设置到ListenerRetriever
	 * @return 匹配支持特定条件(eventType, sourceType)ApplicationListener
	 */
	private Collection<ApplicationListener<?>> retrieveApplicationListeners(
			ResolvableType eventType, @Nullable Class<?> sourceType, @Nullable ListenerRetriever retriever) {
        //返回匹配 ApplicationListener 列表
		List<ApplicationListener<?>> allListeners = new ArrayList<>();

		/** 1 从事件广播器对应的defaultRetriever中获取ApplicationListener 设置到listeners,listenerBeans **/
		//暂存 注册到事件广播器中 ApplicationListener bean对象集合（不重复）
		Set<ApplicationListener<?>> listeners;
		//暂存 注册到事件广播器中 ApplicationListener bean名称集合（不重复）
		Set<String> listenerBeans;
		//从事件广播器对应的defaultRetriever
		synchronized (this.retrievalMutex) {
			listeners = new LinkedHashSet<>(this.defaultRetriever.applicationListeners);
			listenerBeans = new LinkedHashSet<>(this.defaultRetriever.applicationListenerBeans);
		}

		/** 2 遍历注册 ApplicationListener 对象 **/
		for (ApplicationListener<?> listener : listeners) {
			//判断当前监听器是否支持指定事件类型，来源类型
			if (supportsEvent(listener, eventType, sourceType)) {
				if (retriever != null) {
					retriever.applicationListeners.add(listener);
				}
				allListeners.add(listener);
			}
		}

		/** 3 遍历bean名称集合 **/
		if (!listenerBeans.isEmpty()) {
			// 3.1 获取BeanFactory
			ConfigurableBeanFactory beanFactory = getBeanFactory();
			// 3.2 遍历listenerBeans
			for (String listenerBeanName : listenerBeans) {
				try {
					//判断类型为ApplicationListener.class  bean是否支持指定事件类型，来源类型
					// 如果支持
					if (supportsEvent(beanFactory, listenerBeanName, eventType)) {
						//  获取Bean对象
						ApplicationListener<?> listener =
								beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						// 如果不存在，且判断当前监听器支持指定事件类型，来源类型
						if (!allListeners.contains(listener) && supportsEvent(listener, eventType, sourceType)) {
							// 添加到retriever
							if (retriever != null) {
								if (beanFactory.isSingleton(listenerBeanName)) {
									retriever.applicationListeners.add(listener);
								}
								else {
									retriever.applicationListenerBeans.add(listenerBeanName);
								}
							}
							// 添加到allListeners
							allListeners.add(listener);
						}
					}
					// 如果不支持
					else {
						// 获取Bean对象
						Object listener = beanFactory.getSingleton(listenerBeanName);
						// 从retriever中注册ApplicationListener对象集合中删除
						if (retriever != null) {
							retriever.applicationListeners.remove(listener);
						}
						// 从allListeners中删除
						allListeners.remove(listener);
					}
				}
				catch (NoSuchBeanDefinitionException ex) {
				}
			}
		}

		//排序
		AnnotationAwareOrderComparator.sort(allListeners);
		//重置retriever中监听器
		if (retriever != null && retriever.applicationListenerBeans.isEmpty()) {
			retriever.applicationListeners.clear();
			retriever.applicationListeners.addAll(allListeners);
		}
		//返回
		return allListeners;
	}

	/**
	 * 指定Bean 是否支持指定事件类型
	 */
	private boolean supportsEvent(
			ConfigurableBeanFactory beanFactory, String listenerBeanName, ResolvableType eventType) {

		// 获取Bean class 类型
		Class<?> listenerType = beanFactory.getType(listenerBeanName);
		if (listenerType == null || GenericApplicationListener.class.isAssignableFrom(listenerType) ||
				SmartApplicationListener.class.isAssignableFrom(listenerType)) {
			return true;
		}
		//指定监听器的Class是否支持此事件类型
		if (!supportsEvent(listenerType, eventType)) {
			return false;
		}
		//通过BeanDefinition 获取支持事件类型做匹配
		try {
			BeanDefinition bd = beanFactory.getMergedBeanDefinition(listenerBeanName);
			ResolvableType genericEventType = bd.getResolvableType().as(ApplicationListener.class).getGeneric();
			return (genericEventType == ResolvableType.NONE || genericEventType.isAssignableFrom(eventType));
		}
		catch (NoSuchBeanDefinitionException ex) {
			return true;
		}
	}

	/**
     * 指定监听器的Class是否支持此事件类型
	 */
	protected boolean supportsEvent(Class<?> listenerType, ResolvableType eventType) {
		// 获取Class支持事件类型
		ResolvableType declaredEventType = GenericApplicationListenerAdapter.resolveDeclaredEventType(listenerType);
		// 通过获取事件类型判断是否匹配
		return (declaredEventType == null || declaredEventType.isAssignableFrom(eventType));
	}

	/**
	 * 确定给定的侦听器是否支持给定的事件。
	 */
	protected boolean supportsEvent(
			ApplicationListener<?> listener, ResolvableType eventType, @Nullable Class<?> sourceType) {
		// 1 将listener 转换为 GenericApplicationListener
		GenericApplicationListener smartListener = (listener instanceof GenericApplicationListener ?
				(GenericApplicationListener) listener : new GenericApplicationListenerAdapter(listener));

		// 2 判断GenericApplicationListener 是否支持给定的源类型,事件类型
		return (smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType));
	}


	/**
	 * 基于事件类型和源类型的ListenerRetrievers的缓存键。
	 */
	private static final class ListenerCacheKey implements Comparable<ListenerCacheKey> {

		private final ResolvableType eventType;

		@Nullable
		private final Class<?> sourceType;

		public ListenerCacheKey(ResolvableType eventType, @Nullable Class<?> sourceType) {
			Assert.notNull(eventType, "Event type must not be null");
			this.eventType = eventType;
			this.sourceType = sourceType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ListenerCacheKey)) {
				return false;
			}
			ListenerCacheKey otherKey = (ListenerCacheKey) other;
			return (this.eventType.equals(otherKey.eventType) &&
					ObjectUtils.nullSafeEquals(this.sourceType, otherKey.sourceType));
		}

		@Override
		public int hashCode() {
			return this.eventType.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.sourceType);
		}

		@Override
		public String toString() {
			return "ListenerCacheKey [eventType = " + this.eventType + ", sourceType = " + this.sourceType + "]";
		}

		@Override
		public int compareTo(ListenerCacheKey other) {
			int result = this.eventType.toString().compareTo(other.eventType.toString());
			if (result == 0) {
				if (this.sourceType == null) {
					return (other.sourceType == null ? 0 : -1);
				}
				if (other.sourceType == null) {
					return 1;
				}
				result = this.sourceType.getName().compareTo(other.sourceType.getName());
			}
			return result;
		}
	}


	/**
	 * Helper类，它封装一组特定的目标侦听器，
	 *
	 * 可以用来作为基于事件类型和源类型ListenerCacheKey 条件匹配的一组 ApplicationListener
	 * 可以用来作为基于事件广播器注册所有的 ApplicationListener
	 */
	private class ListenerRetriever {

		public final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

		public final Set<String> applicationListenerBeans = new LinkedHashSet<>();

		private final boolean preFiltered;

		public ListenerRetriever(boolean preFiltered) {
			this.preFiltered = preFiltered;
		}

		public Collection<ApplicationListener<?>> getApplicationListeners() {
			List<ApplicationListener<?>> allListeners = new ArrayList<>(
					this.applicationListeners.size() + this.applicationListenerBeans.size());


			allListeners.addAll(this.applicationListeners);
			if (!this.applicationListenerBeans.isEmpty()) {
				BeanFactory beanFactory = getBeanFactory();
				for (String listenerBeanName : this.applicationListenerBeans) {
					try {
						ApplicationListener<?> listener = beanFactory.getBean(listenerBeanName, ApplicationListener.class);
						if (this.preFiltered || !allListeners.contains(listener)) {
							allListeners.add(listener);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Singleton listener instance (without backing bean definition) disappeared -
						// probably in the middle of the destruction phase
					}
				}
			}
			if (!this.preFiltered || !this.applicationListenerBeans.isEmpty()) {
				AnnotationAwareOrderComparator.sort(allListeners);
			}
			return allListeners;
		}
	}

}
