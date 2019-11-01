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

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {


	/**
	 * 单例bean实例缓存  bean name --> bean instance （这里bean实例已实例化完成经过 createBeanInstance(创建)--populateBean(已依赖注入)--initializeBean（初始化）
	 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 早期bean实例工厂缓存 bean name --> ObjectFactory （这里早期bean实例只经过 createBeanInstance(创建)  未populateBean(已依赖注入)  未initializeBean（初始化）
	 **/
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 早期bean实例缓存 bean name --> bean instance （这里早期bean实例只经过 createBeanInstance(创建)  未populateBean(已依赖注入)  未initializeBean（初始化）
	 * 解决【单例循环依赖】的关键所在。
	 */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/**
	 * 单例bean实例缓存中bean名称集合。
	 */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * 正在创建单例Bean的名字的集合，用于解决单例bean循环依赖的问题
	 *   在创建单例bean实例前 调用beforeSingletonCreation添加
	 *   在创建单例bean实例后 调用afterSingletonCreation删除
	 **/
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));


	/** 无需添加到 ingletonsCurrentlyInCreation bean名称集合，也就是无需检查循环依赖的bean名称 */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));


	/** 抑制的异常集合，所谓抑制就时发生异常不抛出保存下来 */
	@Nullable
	private Set<Exception> suppressedExceptions;


	/**
	 * 标识是否正在执行destroySingletons清理单例bean实例缓存过程中
	 *     执行destroySingletons 设置为true
	 *     执行destroySingletons 完毕设置为false
	 */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * 保存实现DisposableBean接口的bean
	 * 在调用destroySingletons清理单例bean实例缓存过程中，会回调destroy()方法
	 */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** 存储包含关系：beanName - > 被包含 beanName 的集合  */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** 存储依赖关系 ：依赖beanName - > 被依赖 beanName 的集合 */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** 存储反相依赖关系  ：被依赖 beanName - > 依赖 beanName 的集合,dependentBeanMap反相存储 */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

	//---------------------------------------------------------------------
	// 单例bean缓存相关方法
	//---------------------------------------------------------------------

	/**
	 * 注册单例bean实例，同时指定名称
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		/** 判断beanName,singletonObject不能为null **/
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		/** 对单例bean对象的高速缓存(单例bean注册表存储容器)加锁 **/
		synchronized (this.singletonObjects) {
			/** 如果注册beanName 已经存在抛出异常 **/
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			/** 注册单例bean实例，同时指定名称 **/
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 注册单例bean实例，同时指定名称
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		/** 对单例bean对象的高速缓存(单例bean注册表存储容器)加锁 **/
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 注册早期bean实例工厂，同时指定名称
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	/**
	 * 从单例bean对象缓存中获取获取指定beanName名称对应bean实例
	 */
	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}


	/**
	 * 从单例bean对象缓存中获取bean实例
	 * @param beanName bean名称
	 * @param allowEarlyReference 是否允许单例bean实例工厂构造bean实例
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		/** 1 从单例bean实例缓存获取指定名称对应bean实例 **/
		Object singletonObject = this.singletonObjects.get(beanName);
		/** 2 beanName缓存中不存在，且beanName并为在创建 **/
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			/** singletonObjects 加锁 **/
			synchronized (this.singletonObjects) {
				/** 3  从早期bean实例缓存中获取 **/
				singletonObject = this.earlySingletonObjects.get(beanName);
				/** 4  指定beanName早期bean实例缓存中不存在，且允许提前创建 **/
				if (singletonObject == null && allowEarlyReference) {
					/** 5 从早期bean实例工厂缓存获取 ObjectFactory **/
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					/**  6 通过singletonFactory 获取bean 实例 **/
					if (singletonFactory != null) {
						/** 7 从singletonFactory 获得 bean 实例 **/
						singletonObject = singletonFactory.getObject();
						/** 8 获得 bean 实例 添加到 早期bean实例缓存 **/
						this.earlySingletonObjects.put(beanName, singletonObject);
						/** 9 从早期bean实例工厂缓存 删除指定 bean  **/
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * 使用singletonFactory 获取早期bean实例
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		/** 对singletonObjects加锁 **/
		synchronized (this.singletonObjects) {
			/** 从单例bean实例缓存获取指定beanName的早期bean实例 **/
			Object singletonObject = this.singletonObjects.get(beanName);
			/** 缓存中不存在  **/
			if (singletonObject == null) {

				/** 当前其他线程调用destroySingletons，销毁所有单例bean 抛出异常 **/
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}

				/** 打印日志 **/
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}

				/**  创建单例 Bean 的前置处理  **/
				beforeSingletonCreation(beanName);

				/** 标识从singletonFactory 获取新的单例bean实例**/
				boolean newSingleton = false;

				/** 初始化suppressedExceptions  **/
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				/** 使用singletonFactory获取单例bean 实例 **/
				try {
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					/** 从单例bean注册表获取指定beanName名称的bean实例 **/
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					/** 将 BeanCreationException 添加到 suppressedExceptions **/
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					/** 重置suppressedExceptions **/
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					/**  创建单例 Bean  的后置处理  **/
					afterSingletonCreation(beanName);
				}
				/** 注册单例bean实例，同时指定名称 **/
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			/** 返回singletonObject **/
			return singletonObject;
		}
	}

	/**
	 * 注册一个Exception到 suppressedExceptions(抑制的异常列表)
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 将指定beanName,从单例bean缓存中删除
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	/**
	 * 指定bean名称是否存在于单例bean实例缓存
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}



	/**
	 * 单例bean实例缓存中bean名称集合。
	 */
	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	/**
	 * 获取单例bean实例缓存bean数量
	 */
	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}

	/**
	 * 获取单例bean实例缓存
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}


	//---------------------------------------------------------------------
	// 正在创建单例Bean的名字的集合相关方法
	//---------------------------------------------------------------------

	/**
	 * 注册/删除 无需添加到 ingletonsCurrentlyInCreation bean名称集合，也就是无需检查循环依赖的bean名称
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 指定bean名称是否发生循环依赖，也就是指定bean名称存在于正在创建单例Bean的名字的集合中，排除掉inCreationCheckExclusions集合
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	/**
	 * 指定bean名称是否发生循环依赖，也就是指定bean名称存在于正在创建单例Bean的名字的集合中
	 */
	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}


	/**
	 * 指定bean名称是否发生循环依赖，也就是指定bean名称存在于正在创建单例Bean的名字的集合中
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 模板方法，单例bean创建前置动作，子类可以覆盖
	 * 默认实现：将beanName添加singletonsCurrentlyInCreation(当前正在创建的原型bean的名称)
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 模板方法，单例bean创建后置动作，子类可以覆盖
	 * 默认实现：将beanName从singletonsCurrentlyInCreation删除(当前正在创建的原型bean的名称)
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	//---------------------------------------------------------------------
	// 注册实现 DisposableBean 接口的单例bean
	//---------------------------------------------------------------------

	/**
	 * 注册实现 DisposableBean 接口的单例bean
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}


	//---------------------------------------------------------------------
	// 注册单例bean依赖关系
	//---------------------------------------------------------------------

	/**
	 * 注册包含关系
	 *   containedBeanName 表示依赖bean
	 *   containingBeanName 表示被依赖bean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		/** 为bean注册一个依赖的dependentBeanName  **/
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 为bean注册一个依赖的dependentBeanName
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		/** 获取name作为别名对应bean名称  **/
		String canonicalName = canonicalName(beanName);

		/** 注册 beanName --> dependentBeanName 正向依赖关系 **/
		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}
		/** 注册 dependentBeanName --> beanName  反向依赖关系 **/
		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 校验该依赖关系
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	/**
	 * 校验该依赖关系
	 */
	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		/** 获取name作为别名对应bean名称 **/
		String canonicalName = canonicalName(beanName);

		/** 获取当前 beanName 的依赖集合 **/
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}

		/** 判断dependentBeanName，是否存在beanName 的依赖集合中 **/
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}

		/** 递归检测依赖 **/
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	//---------------------------------------------------------------------
	// 销毁单例bean方法
	//---------------------------------------------------------------------

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * 清理所有单实例bean缓存
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 销毁指定beanName 单例bean实例.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		/** 将指定beanName,从单例bean缓存中删除 **/
		removeSingleton(beanName);

		/** 获取beanName 对应DisposableBean，并从disposableBeans删除 **/
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		/** 销毁指定beanName 单例bean实例. **/
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 销毁给定bean.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;

		/** 将beanName 指定从dependentBeanMap删除 **/
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}

		/** 销毁指定beanName 所有依赖bean **/
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		/** 执行DisposableBean 销毁动作 **/
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		/**  从 containedBeanMap 中删除 **/
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		/**  从 dependentBeanMap 中删除 **/
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		/** 从dependenciesForBeanMap中删除 **/
		this.dependenciesForBeanMap.remove(beanName);
	}



}
