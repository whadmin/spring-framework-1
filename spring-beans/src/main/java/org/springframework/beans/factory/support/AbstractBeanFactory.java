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

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** 父BeanFactory */
	@Nullable
	private BeanFactory parentBeanFactory;

	/** 加载bean对象 ClassLoader */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** 临时 ClassLoader */
	@Nullable
	private ClassLoader tempClassLoader;

	/** 是否将获取RootBeanDefinition，缓存到mergedBeanDefinitions  */
	private boolean cacheBeanMetadata = true;

	/** bean定义值中表达式的解析策略。 */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/** Spring 类型转换的服务  */
	@Nullable
	private ConversionService conversionService;

	/** 定制PropertyEditorRegistrars */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/** 定制 PropertyEditors  */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** 自定义TypeConverter，将覆盖默认的PropertyEditor机制 */
	@Nullable
	private TypeConverter typeConverter;

	/** 字符串解析器列表*/
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/** 已注册 BeanPostProcessors  */
	private final List<BeanPostProcessor> beanPostProcessors = new CopyOnWriteArrayList<>();

	/** 是否已注册InstantiationAwareBeanPostProcessor。 */
	private volatile boolean hasInstantiationAwareBeanPostProcessors;

	/** 是否已注册DestructionAwareBeanPostProcessors*/
	private volatile boolean hasDestructionAwareBeanPostProcessors;

	/** 保存当前BeanFactory 支持的作用域 映射 */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/** 与SecurityManager一起运行时使用的安全上下文。 */
	@Nullable
	private SecurityContextProvider securityContextProvider;

	/**
	 * 合并后BeanDefinitions缓存 对应关系为 bean name --> RootBeanDefinition
	 * 由于BeanDefinitions 存在父子层级关系，在获取Bean之前需要当前bean对应BeanDefinition和父BeanDefinitions进行合并
	 * */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/** 已创建 Bean 的名字集合 */
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** 当前正在创建的原型bean的名称 */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * 创建一个新的AbstractBeanFactory。
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * 创建一个新的AbstractBeanFactory。并指定父BeanFactory
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// BeanFactory接口的实现
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {
		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * 根据名称，类型，参数获取bean实例
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

		/** 获取name作为别名对应bean名称 **/
		final String beanName = transformedBeanName(name);
		Object bean;

		/** 从单例bean对象缓存中获取获取指定beanName名称对应bean实例 **/
		Object sharedInstance = getSingleton(beanName);

		/** 1 单例bean对象缓存中存在 获取bean对象**/
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			/** 2 获取bean对象，这里主要是针对实现FactoryBean接口特殊Bean处理 **/
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}
		/** 2 单例bean对象缓存中不存在bean实例 **/
		else {
			/** 2.1 检查beanName是否作为原型bean。发生循环依赖**/
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			/** 2.2.如果没有注册BeanDefinition到BeanFacory，从父BeanFactory获取Bean **/
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			/** 2.3 将beanName  标记为已创建 **/
			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			try {
				/** 2.2.4 获取指定beanName  BeanDefinition（合并的），并检查 BeanDefinition 非抽象 **/
				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				/** 2.2.5  处理beanName 依赖DependsOn  **/
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {

						/** 校验该依赖关系 **/
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						/** 如果校验成功，将该依赖进行注册 **/
						registerDependentBean(dep, beanName);

						/** 尝试实例化依赖 Bean 对象 **/
						try {
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				/** 2.2.6.1 如果是单例bean **/
				if (mbd.isSingleton()) {
					/** 使用singletonFactory 获取早期bean实例 **/
					sharedInstance = getSingleton(beanName, () -> {
						try {
							/** 创建bean **/
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							/** 销毁beanName 单例bean实例. **/
							destroySingleton(beanName);
							throw ex;
						}
					});
					/** 2 从bean实例获取构造对象（处理FactoryBean逻辑） **/
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				/** 2.2.6.2 如果是原型bean **/
				 else if (mbd.isPrototype()) {
					Object prototypeInstance = null;
					try {
						/**
						 * 模板方法，原型bean创建前置动作，子类可以覆盖
						 * 默认实现：将beanName添加prototypesCurrentlyInCreation(当前正在创建的原型bean的名称)
						 */
						beforePrototypeCreation(beanName);

						/** 创建bean **/
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						/**
						 * 模板方法，原型bean创建后置动作，子类可以覆盖
						 * 默认实现：将beanName从prototypesCurrentlyInCreation删除(当前正在创建的原型bean的名称)
						 */
						afterPrototypeCreation(beanName);
					}
					/** 2 从bean实例获取构造对象（处理FactoryBean逻辑） **/
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}
                /** 2.2.6.3 如果是扩展作用域  request/session**/
				else {
					/** 获取bean作用域  单例/原型 **/
					String scopeName = mbd.getScope();
					final Scope scope = this.scopes.get(scopeName);
					/** 没有设置作用域 抛出异常 **/
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							/**
							 * 模板方法，原型bean创建前置动作，子类可以覆盖
							 * 默认实现：将beanName添加prototypesCurrentlyInCreation(当前正在创建的原型bean的名称)
							 */
							beforePrototypeCreation(beanName);
							try {
								/** 创建bean **/
								return createBean(beanName, mbd, args);
							}
							finally {
								/**
								 * 模板方法，原型bean创建后置动作，子类可以覆盖
								 * 默认实现：将beanName从prototypesCurrentlyInCreation删除(当前正在创建的原型bean的名称)
								 */
								afterPrototypeCreation(beanName);
							}
						});
						/** 2 从bean实例获取构造对象（处理FactoryBean逻辑） **/
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new BeanCreationException(beanName,
								"Scope '" + scopeName + "' is not active for the current thread; consider " +
								"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
								ex);
					}
				}
			}
			catch (BeansException ex) {
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		/** 3 检查所需的类型是否与实际bean实例的类型匹配。如果不匹配使用TypeConverter尝试进行类型转换 **/
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}

	/**
	 *  获取bean对象，这里主要是针对实现FactoryBean接口特殊Bean处理
	 *  对于FactoryBean
	 * 	  getBean("car")，由于FactoryBean实例本身就是一个工厂会调用getObject()返回工厂构造对象
	 * 	  getBean("&car") 只需要加上前缀返回FactoryBean实例本身
	 * 对于非FactoryBean
	 *    直接返回参数 beanInstance
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		/** 1  对于FactoryBean  getBean("&car") 只需要加上前缀返回FactoryBean实例本身 **/

		/** bean名称是否存在"&"前缀**/
		if (BeanFactoryUtils.isFactoryDereference(name)) {

			/** 如果bean实例 类型为 NullBean 直接返回 **/
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}

			/** 如果bean实例 类型不是 FactoryBean 抛出异常 **/
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			/** 设置RootBeanDefinition 当前bean实例为FactoryBean **/
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}
			/** 返回FactoryBean实例本身 **/
			return beanInstance;
		}


		/** 2 对于非FactoryBean 直接返回beanInstance **/
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

		/** 3  对于FactoryBean  getBean("car")，由于FactoryBean实例本身就是一个工厂会调用getObject()返回工厂构造对象 **/

		/** 定义返回Objext对象**/
		Object object = null;

		/** 设置RootBeanDefinition 当前bean实例为FactoryBean **/
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
			/** 从FactoryBean构造对象缓存获取 **/
			object = getCachedObjectForFactoryBean(beanName);
		}

		/** 缓存不存在，通过beanInstance获取object,并放入缓存 **/
		if (object == null) {
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;

			/**
			 * 获取指定beanName  BeanDefinition
			 * 由于BeanDefinitions 存在父子层级关系，在获取Bean之前需要当前bean对应BeanDefinition和父BeanDefinitions进行合并
			 * **/
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			/** 返回此bean定义是否是“合成的”，即不是由应用程序本身定义的。 **/
			boolean synthetic = (mbd != null && mbd.isSynthetic());

			/**  通过FactoryBean实例 获取bean实例  **/
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * 判断指定名称bean，是否注册到BeanFactory
	 */
	@Override
	public boolean containsBean(String name) {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 1 判断是是否存在于单例bean实例缓存中或已在IOC容器注册BeanDefinition **/
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			/**  1.1 bean名称不以“&”作为前缀返回true **/
			/**  1.2 bean名称以“&”作为前缀且bean类型为FactoryBean返回true **/
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		/** 2 如果不存在，判断是否注册到父BeanFactory判断 **/
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	/**
	 * bean 是否被定义成单实例
	 */
	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 1 从单例bean对象缓存中获取bean实例，通过获取bean实例判断是否被定义成单实例  **/
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			/** 1.1 bean实例类型为 FactoryBean **/
			if (beanInstance instanceof FactoryBean) {
				/**  名称以&作为前缀返回true **/
				/**  名称是不以&作为前缀 返回((FactoryBean<?>) beanInstance).isSingleton() **/
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			/** 1.2 bean实例类型非 FactoryBean **/
			else {
				/** bean名称不以“&”作为前缀返回true **/
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		/** 2 如果没有注册BeanDefinition到BeanFacory，从父BeanFactory判断是否被定义成单实例 **/
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		/**
		 * 3 从合并后BeanDefinitions缓存中获取合并后BeanDefinitions，通过mbd.isSingleton()判断是否被定义成单实例 **/
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		/** BeanDefinitions 定义成单实例  **/
		if (mbd.isSingleton()) {
			/** 3.1.1 bean实例类型为 FactoryBean**/
			if (isFactoryBean(beanName, mbd)) {
				/**  名称以&作为前缀返回true **/
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				/**  名称是不以&作为前缀 返回((FactoryBean<?>) beanInstance).isSingleton() **/
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			/** 3.1.2 bean实例类型非 FactoryBean**/
			else {
				/** bean名称不以“&”作为前缀返回true **/
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		/** BeanDefinitions 定义成非单实例  **/
		else {
			return false;
		}
	}

	/**
	 * bean 是否被定义成原型实例
	 */
	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 1 如果没有注册BeanDefinition到BeanFacory，从父BeanFactory判断是否被定义成原型实例 **/
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		/**
		 * 2 从合并后BeanDefinitions缓存中获取合并后BeanDefinitions，通过mbd.isPrototype()判断是否被定义成原型实例
		 *    2.1 如果传入的名称以&作为前缀，表示获取Bean类型可能是FactoryBean，需要核对bean类型正常返回true
		 *    2.1 如果传入的名称不以&作为前缀，直接返回true **/
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		/**
		 * 3 合并后BeanDefinitions定义成非原型实例
		 *   3.1 如果传入的名称以&作为前缀，直接返回false
		 *   3.2 如果传入的名称不以&作为前缀,获取FactoryBean对象，通过isPrototype()判断**/
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			final FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
						((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) || !fb.isSingleton()),
						getAccessControlContext());
			}
			else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}


	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);
		/** 获取传入name名称是否以&作为前缀 **/
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);


		/** 1 从单例bean对象缓存中获取bean实例，通过获取bean实例判断类型是否匹配  **/
		Object beanInstance = getSingleton(beanName, false);
		/**   如果存在于单例bean对象缓存中且Class类型不为NullBean **/
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			/**  如果类型是FactoryBean **/
			if (beanInstance instanceof FactoryBean) {
				/** 如果名称不以&作为前缀，通过判断factoryBean.getObjectType()构造对象类型进行匹配 **/
				if (!isFactoryDereference) {
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				/** 如果名称以&作为前缀，通过factoryBean对象类型进行匹配 **/
				else {
					return typeToMatch.isInstance(beanInstance);
				}
			}
			/**  如果类型非FactoryBean **/
			else if (!isFactoryDereference) {
				/** 通过实例类型进行匹配 **/
				if (typeToMatch.isInstance(beanInstance)) {
					return true;
				}
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					Class<?> targetType = mbd.getTargetType();
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						Class<?> classToMatch = typeToMatch.resolve();
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							return false;
						}
						if (typeToMatch.isAssignableFrom(targetType)) {
							return true;
						}
					}
					ResolvableType resolvableType = mbd.targetType;
					if (resolvableType == null) {
						resolvableType = mbd.factoryMethodReturnType;
					}
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			return false;
		}
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			return false;
		}

		/** 2 如果没有注册BeanDefinition到BeanFacory，从父BeanFactory判断bean类型是否匹配 **/
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		/** 获取匹配Class类型 数组 **/
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		else if (isFactoryDereference) {
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// 如果我们有一个bean类型，可以使用它来考虑泛型
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// 如果我们没有bean类型，则回退到预测类型
		return typeToMatch.isAssignableFrom(predictedType);
	}



	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	/**
	 * 根据bean的名字，获取在IOC容器中得到bean实例Class类型
	 */
	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 1  从单例bean对象缓存中获取bean实例，如果存在，通过获取bean实例判断其Class类型 **/
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			/** bean实例类型为 FactoryBean 且 名称不以&作为前缀 通过factoryBean.getObjectType()获取创建对象Class类型  **/
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			/** bean实例类型非 FactoryBean，通过beanInstance.getClass()获取bean实例Class类型 **/
			else {
				return beanInstance.getClass();
			}
		}

		/** 2 优先从父BeanFactory获取bean实例Class类型  **/
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.getType(originalBeanName(name));
		}


		/** 3  前面2步获取失败，这里正式开始从当前IOC获取Bean Class类型  **/
		/** 3.1 获取合并 RootBeanDefinition，通过RootBeanDefinition获取 Bean Class类型**/
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		//如果dbd存在，且参数并非&name，获取工厂Bean类型
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		/** 3 从合并后BeanDefinitions缓存中获取合并后BeanDefinitions，对于FactoryBean.class
		 *  对于FactoryBean类型及其子类
		 *   如果传入的名称不以&作为前缀，通过getTypeForFactoryBean获取类型
		 *   如果传入的名称以&作为前缀，通过predictBeanType获取类型
		 * 对于非FactoryBean类型及其子类
		 *   如果传入的名称以&作为前缀，返回null
		 *   如果传入的名称不以&作为前缀，通过predictBeanType获取类型
		 *   **/
		Class<?> beanClass = predictBeanType(beanName, mbd);
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass;
			}
		}
		else {
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	/**
	 * 根据bean的名字，获取别名
	 */
	@Override
	public String[] getAliases(String name) {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 定义返回的别名列表 **/
		List<String> aliases = new ArrayList<>();

		/** 名称是否以&作为前缀 **/
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);

		/** 定义bean全名称（如果）  **/
		String fullBeanName = beanName;
		/** 如果名称是否以&作为前缀,bean全名称追加&作为前缀 **/
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		/** 1 如果传入name是别名(beanName!=name)添加到aliases列表 **/
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		/** 2 获取指定名称的别名 **/
		String[] retrievedAliases = super.getAliases(beanName);
		for (String retrievedAlias : retrievedAliases) {
			/** 如果名称是否以&作为前缀,返回别名追加&作为前缀 **/
			String alias = (factoryPrefix ? FACTORY_BEAN_PREFIX : "") + retrievedAlias;
			/** 别名不等于传入的name,添加到列表 **/
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		/** 3 如果指定name在当前BeanFactory未定义，从父BeanFactory获取别名添加到别名列表  **/
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		/** 4 将列表转换为数组返回 **/
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// HierarchicalBeanFactory接口的实现
	//---------------------------------------------------------------------

	/**
	 * 返回父BeanFactory
	 */
	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	/**
	 * @param name
	 * @return
	 */
	@Override
	public boolean containsLocalBean(String name) {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// ConfigurableBeanFactory接口的实现
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}


	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}


	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}


	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}


	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}


	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}


	@Override
	public TypeConverter getTypeConverter() {
		/** 1 获取自定义TypeConverter，如果存在返回 **/
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		/** 2 如果没有自定义TypeConverter，则创建一个默认SimpleTypeConverter，初始化并返回 **/
		else {
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	/**
	 * 使用StringValueResolver解析参数
	 */
	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		this.beanPostProcessors.remove(beanPostProcessor);
		if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
			this.hasInstantiationAwareBeanPostProcessors = true;
		}
		if (beanPostProcessor instanceof DestructionAwareBeanPostProcessor) {
			this.hasDestructionAwareBeanPostProcessors = true;
		}
		this.beanPostProcessors.add(beanPostProcessor);
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}


	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return this.hasInstantiationAwareBeanPostProcessors;
	}

	protected boolean hasDestructionAwareBeanPostProcessors() {
		return this.hasDestructionAwareBeanPostProcessors;
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}


	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}


	@Override
	public AccessControlContext getAccessControlContext() {
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.hasInstantiationAwareBeanPostProcessors = this.hasInstantiationAwareBeanPostProcessors ||
					otherAbstractFactory.hasInstantiationAwareBeanPostProcessors;
			this.hasDestructionAwareBeanPostProcessors = this.hasDestructionAwareBeanPostProcessors ||
					otherAbstractFactory.hasDestructionAwareBeanPostProcessors;
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}


	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		String beanName = transformedBeanName(name);
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		return getMergedLocalBeanDefinition(beanName);
	}

	/**
	 * 判断bean类型是否为FactoryBean
	 */
	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		/** 获取name作为别名对应bean名称 **/
		String beanName = transformedBeanName(name);

		/** 1 单例bean对象缓存中获取bean实例，并判断类型是否为FactoryBean  **/
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}

		/** 2 获取父BeanFactory,迭代调用isFactoryBean,确定指定名称bean类型为FactoryBean **/
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}
		/** 3 通过检查BeanDefinition，确定指定名称bean类型为FactoryBean  **/
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * 指定bean是否发生循环依赖
	 */
	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * 返回指定的原型bean是否当前正在创建（在当前线程内）。发生循环依赖
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * 模板方法，原型bean创建前置动作，子类可以覆盖
	 * 默认实现：将beanName添加prototypesCurrentlyInCreation(当前正在创建的原型bean的名称)
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * 模板方法，原型bean创建后置动作，子类可以覆盖
	 * 默认实现：将beanName从prototypesCurrentlyInCreation删除(当前正在创建的原型bean的名称)
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}


	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * 获取name作为别名对应bean名称
	 */
	protected String transformedBeanName(String name) {
		/**对于存在&前缀name需要先剥离后调用canonicalName获取bean名称  **/
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}


	/**
	 * 获取name作为别名对应bean名称
	 */
	protected String originalBeanName(String name) {
		/**  对于存在&前缀name需要先剥离后调用canonicalName获取规范名称**/
		String beanName = transformedBeanName(name);
		/** 如果存在&前缀，在获取bean名称后将&前缀追加到bean名称前 **/
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * 初始化BeanWrapper
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		/** 设置ConversionService **/
		bw.setConversionService(getConversionService());
		/** 注册CustomEditors **/
		registerCustomEditors(bw);
	}

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		PropertyEditorRegistrySupport registrySupport =
				(registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);
		if (registrySupport != null) {
			registrySupport.useConfigValueEditors();
		}
		if (!this.propertyEditorRegistrars.isEmpty()) {
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					registrar.registerCustomEditors(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * 获取指定beanName BeanDefinition（合并后的）
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		/** 从缓存中获取 **/
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			return mbd;
		}
		/**
		 * 获取BeanDefinition（合并后的）
		 * 1  返回指定名称对应已注册 BeanDefinition（未合并）
		 * 2  调用getMergedBeanDefinition 获取BeanDefinition（合并后的）
		 * **/
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 * 获取指定beanName  BeanDefinition（合并后的）
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {
		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * 获取指定beanName 对应 BeanDefinition（合并后的）
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		/** 加锁 **/
		synchronized (this.mergedBeanDefinitions) {
			/** 定义合并返回 BeanDefinition **/
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous = null;

			/** 尝试从缓存获取 **/
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}
            /** 如果缓存中不存在  **/
			if (mbd == null || mbd.stale) {
				previous = mbd;
				mbd = null;
                /** 如果bd不存在父BeanDefinition，将其封装成RootBeanDefinition */
				if (bd.getParentName() == null) {
					if (bd instanceof RootBeanDefinition) {
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {
						mbd = new RootBeanDefinition(bd);
					}
				}
				/** 如果bd存在父BeanDefinition，将其封装成RootBeanDefinition */
				else {
					BeanDefinition pbd;
					try {
						/** 获取parentBeanName作为别名对应bean名称 **/
						String parentBeanName = transformedBeanName(bd.getParentName());

						if (!beanName.equals(parentBeanName)) {
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						else {
							BeanFactory parent = getParentBeanFactory();
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
										"': cannot be resolved without an AbstractBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					mbd = new RootBeanDefinition(pbd);
					mbd.overrideFrom(bd);
				}

				/** 若前面没配置scope类型，这里设置为单例类型 **/
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(RootBeanDefinition.SCOPE_SINGLETON);
				}

				/** 如果BeanDefinition scope类型发生变更，重置缓存中设置 **/
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}

				/** 放入mergedBeanDefinitions缓存 **/
				if (containingBd == null && isCacheBeanMetadata()) {
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}

			/** 将previous 合并到 mbd **/
			if (previous != null) {
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			return mbd;
		}
	}

	/**
	 *  将previous 合并到 mbd
	 */
	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 * 检查指定BeanDefinition 是否是抽象的
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * 从合并后BeanDefinitions缓存删除指定beanName
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * 从合并后BeanDefinitions缓存中删除不存在于已创建bean
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * 确定指定bean是否存在于已创建Bean名称中
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * 解析Bean Class
	 */
	@Nullable
	protected Class<?> resolveBeanClass(final RootBeanDefinition mbd, String beanName, final Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			/** 设置Bean Class 直接返回 **/
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>) () ->
					doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		/** 获取ClassLoader  **/
		ClassLoader beanClassLoader = getBeanClassLoader();

		ClassLoader dynamicLoader = beanClassLoader;

		boolean freshResolve = false;

		/** 如果typesToMatch不为Null且tempClassLoader类型为自定义类加载器，添加typesToMatch放入tempClassLoader类加载器，排除加载的类名单中 **/
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				dynamicLoader = tempClassLoader;
				freshResolve = true;
				if (tempClassLoader instanceof DecoratingClassLoader) {
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		/** 获取BeanClassName **/
		String className = mbd.getBeanClassName();
		if (className != null) {
			/** 使用beanExpressionResolver解析ClassName **/
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			/**  **/
			if (!className.equals(evaluated)) {
				if (evaluated instanceof Class) {
					return (Class<?>) evaluated;
				}
				else if (evaluated instanceof String) {
					className = (String) evaluated;
					freshResolve = true;
				}
				else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			/** 使用dynamicLoader，dynamicLoader默认情况下指向beanClassLoader加载，如果设置typesToMatch dynamicLoader指向TempClassLoader **/
			if (freshResolve) {
				if (dynamicLoader != null) {
					try {
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				return ClassUtils.forName(className, dynamicLoader);
			}
		}
        /** 使用BeanClassLoader加载beanClass 返回Class对象 **/
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * 使用beanExpressionResolver解析ClassName（???）
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		if (this.beanExpressionResolver == null) {
			return value;
		}

		Scope scope = null;
		if (beanDefinition != null) {
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 * 推断Bean的Class类型
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		/** 从getTargetType()获取Bean的类型 **/
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
        /** 如果是通过静态工厂或实例工厂方法构造Bean返回null **/
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		/** 解析Bean Class **/
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * 通过检查BeanDefinition，确定指定名称bean类型为FactoryBean
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		/** 从BeanDefinition定义中获取类型是否为FactoryBean **/
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			/** 预测Bean的Class类型  **/
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			/** 判断类型是否为FactoryBean **/
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		/** 返回结果 **/
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. Implementations are only allowed to instantiate the factory bean if
	 * {@code allowInit} is {@code true}, otherwise they should try to determine the
	 * result through other means.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} if set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it. If
	 * subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails, a
	 * full FactoryBean creation as performed by this implementation should be used as
	 * fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null) ? ResolvableType.forClass(objectType) : ResolvableType.NONE;
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on non-lazy FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * 将指定的beanName 标记为已创建
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		/** 如果beanName不存在已创建名称集合 **/
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				/** double check 如果beanName不存在已创建名称集合 **/
				if (!this.alreadyCreated.contains(beanName)) {
					/** 从合并后BeanDefinitions缓存删除指定beanName **/
					clearMergedBeanDefinition(beanName);
					/** 添加到已创建名称集合 **/
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * 指定名称bean创建失败后，从已创建 Bean 的名字集合中删除
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}



	/**
	 * 删除给定bean名称的单例实例,但前提是不存在于已创建 Bean 的名字集合中。
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * 已创建 Bean 的名字集合不为空
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}



	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class &&
				(DisposableBeanAdapter.hasDestroyMethod(bean, mbd) || (hasDestructionAwareBeanPostProcessors() &&
						DisposableBeanAdapter.hasApplicableProcessors(bean, getBeanPostProcessors()))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				registerDisposableBean(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
			else {
				// A bean with a custom scope...
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName,
						new DisposableBeanAdapter(bean, beanName, mbd, getBeanPostProcessors(), acc));
			}
		}
	}


	//---------------------------------------------------------------------
	// 子类要实现的抽象方法
	//---------------------------------------------------------------------

	/**
	 * 指定beanName是否已注册 BeanDefinition（未合并）
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * 返回指定名称对应已注册 BeanDefinition（未合并）
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * 根据Bean名称，参数，合并后BeanDefinition创建bean实例
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;

}
