/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.lang.Nullable;

/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/**  通过FactoryBeans创建的单例bean的缓存：key为FactoryBean名称。value为FactoryBean.getObject()创建的单例bean实例 */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * 返回此FactoryBean创建的对象的类型
	 */
	@Nullable
	protected Class<?> getTypeForFactoryBean(final FactoryBean<?> factoryBean) {
		try {
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Class<?>>)
						factoryBean::getObjectType, getAccessControlContext());
			}
			else {
				return factoryBean.getObjectType();
			}
		}
		catch (Throwable ex) {
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * 从FactoryBeans创建的单例对象的缓存获取构建bean对象
	 */
	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * 从给定的FactoryBean中获取其管理的object
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		/** 判断FactoryBean管理的对象是否是单例，且beanName存在单例bean对象的高速缓存中 **/
		if (factory.isSingleton() && containsSingleton(beanName)) {
			/** 来锁定singletonObjects这个集合 **/
			synchronized (getSingletonMutex()) {
				/** 通过 beanName 从factoryBeanObjectCache 缓存(通过FactoryBeans创建的单例bean的缓存) 获取 object **/
				Object object = this.factoryBeanObjectCache.get(beanName);
				/** 如果缓存中不存在 **/
				if (object == null) {
					/** 从FactoryBean获取管理实例  **/
					object = doGetObjectFromFactoryBean(factory, beanName);
					/**
					 * 因为有可能在调用doGetObjectFromFactoryBean的过程中,该Bean被放到了factoryBeanObjectCache中,
					 * 所以需要再次校验,通过 beanName 从factoryBeanObjectCache 缓存 获取 object,果此时发现缓存中已经有了,那就直接返回 **/
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						object = alreadyThere;
					}
					else {
						/** 是否需要在获取FactoryBean管理的对象后进行后置处理 **/
						if (shouldPostProcess) {
							if (isSingletonCurrentlyInCreation(beanName)) {
								return object;
							}
							beforeSingletonCreation(beanName);

							/**  模板方法，用来对factoryBean拿到object后对其后置处理逻辑 AbstractAutowireCapableBeanFactory对其进行覆盖实现 **/
							try {
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								afterSingletonCreation(beanName);
							}
						}
						if (containsSingleton(beanName)) {
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				return object;
			}
		}
		/** FactoryBean管理的对象是否不是单例或beanName不存在单例bean对象的高速缓存中 **/
		else {
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			/** 是否需要在获取FactoryBean管理的对象后进行后置处理 **/
			if (shouldPostProcess) {
				try {
					/**  模板方法，用来对factoryBean拿到object后对其后置处理逻辑 AbstractAutowireCapableBeanFactory对其进行覆盖实现 **/
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}

	/**
	 * 从FactoryBean获取管理实例
	 */
	private Object doGetObjectFromFactoryBean(final FactoryBean<?> factory, final String beanName)
			throws BeanCreationException {
		Object object;
		/** 调用factory.getObject()获取FactoryBean管理实例 **/
		try {
			if (System.getSecurityManager() != null) {
				AccessControlContext acc = getAccessControlContext();
				try {
					object = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) factory::getObject, acc);
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				object = factory.getObject();
			}
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}
		/** 如果factory.getObject()返回null **/
		if (object == null) {
			/** beanName 否存在于singletonsCurrentlyInCreation（当前正在创建的bean的名称),抛出异常**/
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			/** 不存在返回NullBean **/
			object = new NullBean();
		}
		return object;
	}

	/**
	 * 模板方法，用来对factoryBean拿到object后对其后置处理逻辑，
	 * AbstractAutowireCapableBeanFactory对进行实现。
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * 如果beanInstance类型FactoryBean，将强制转换(FactoryBean<?>) beanInstance返回
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return (FactoryBean<?>) beanInstance;
	}

	/**
	 * 清理指定beanName单实例缓存
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanObjectCache.remove(beanName);
		}
	}

	/**
	 * 清理所有单实例缓存
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanObjectCache.clear();
		}
	}

	/**
	 * 获取AccessControlContext
	 */
	protected AccessControlContext getAccessControlContext() {
		return AccessController.getContext();
	}

}
