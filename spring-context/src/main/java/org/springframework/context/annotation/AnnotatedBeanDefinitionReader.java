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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 名称来看可以理解为，读取注解定义的Bean，其核心功能如下
 * 1 读取注解定义的Bean,将其解析成BeanDefinition注册到BeanFactory，这里会解析注解定义的Bean上如下注解
 * @Lazy，@DependsOn，@Role，@Description,@Scope 将其配置信息写入BeanDefinition
 *
 * 2 向BeanDefinitionRegistry(默认实现DefaultListableBeanFactory）注册注解配置处理器,这里处理器作为单例Bean被管理
 *
 * 这些处理器在bean实例化时基于开闭原则进行功能扩展其中包括
 *
 * ConfigurationClassPostProcessor
 * AutowiredAnnotationBeanPostProcessor
 * CommonAnnotationBeanPostProcessor
 * PersistenceAnnotationBeanPostProcessor
 * EventListenerMethodProcessor
 * DefaultEventListenerFactory
 *
 */
public class AnnotatedBeanDefinitionReader {

	/**
	 * BeanDefinition注册表，（默认实现DefaultListableBeanFactory）
	 */
	private final BeanDefinitionRegistry registry;

	/**
	 * bean名称生成器
	 */
	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	/**
	 * 作用域解析器 (如果注解定义的Bean配置{@link Scope}注解，通过调用resolveScopeMetadata方法解析获取作用域元数据)
	 */
	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	/**
	 * 条件处理器 （处理Bean标识{@link Condition}注解，
	 */
	private ConditionEvaluator conditionEvaluator;


	/**
	 * 为指定BeanDefinitionRegistry实例化一个新的{@code AnnotatedBeanDefinitionReader}
	 * 如果指定BeanDefinitionRegistry类型为{@link EnvironmentCapable}, 从BeanDefinitionRegistry获取环境配置Environment用于创建内部conditionEvaluator
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));
	}

	/**
	 * 为指定BeanDefinitionRegistry实例化一个新的{@code AnnotatedBeanDefinitionReader}，并指定{@link Environment}。用于创建内部conditionEvaluator
	 */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		Assert.notNull(environment, "Environment must not be null");
		this.registry = registry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
		/** 注册注解配置处理器器， **/
		AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
	}


	/**
	 * 返回BeanDefinitionRegistry
	 */
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	/**
	 * 设置Environment重置conditionEvaluator
	 */
	public void setEnvironment(Environment environment) {
		this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
	}

	/**
	 * 设置Bean名称生成器
	 */
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	/**
	 * 设置作用域{@link Scope @Scope}解析器
	 */
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}



	public void register(Class<?>... annotatedClasses) {
		for (Class<?> annotatedClass : annotatedClasses) {
			registerBean(annotatedClass);
		}
	}


	public void registerBean(Class<?> annotatedClass) {
		doRegisterBean(annotatedClass, null, null, null, null);
	}


	public void registerBean(Class<?> annotatedClass, @Nullable String name) {
		doRegisterBean(annotatedClass, name, null, null, null);
	}


	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> annotatedClass, Class<? extends Annotation>... qualifiers) {
		doRegisterBean(annotatedClass, null, qualifiers, null, null);
	}


	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> annotatedClass, @Nullable String name,
			Class<? extends Annotation>... qualifiers) {

		doRegisterBean(annotatedClass, name, qualifiers, null, null);
	}


	public <T> void registerBean(Class<T> annotatedClass, @Nullable Supplier<T> supplier) {
		doRegisterBean(annotatedClass, null, null, supplier, null);
	}


	public <T> void registerBean(Class<T> annotatedClass, @Nullable String name, @Nullable Supplier<T> supplier) {
		doRegisterBean(annotatedClass, name, null, supplier, null);
	}


	public <T> void registerBean(Class<T> annotatedClass, @Nullable String name, @Nullable Supplier<T> supplier,
			BeanDefinitionCustomizer... customizers) {

		doRegisterBean(annotatedClass, name, null, supplier, customizers);
	}

	/**
	 * 将指定被注解修饰Class，解析成BeanDefinition注册到BeanFactory
	 * 同时会解析@Scope，@Condition注解
	 */
	private <T> void doRegisterBean(Class<T> annotatedClass, @Nullable String name,
			@Nullable Class<? extends Annotation>[] qualifiers, @Nullable Supplier<T> supplier,
			@Nullable BeanDefinitionCustomizer[] customizers) {

		/** 创建AnnotatedGenericBeanDefinition实例，AnnotatedGenericBeanDefinition用来表示被注解定义Bean描述  **/
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(annotatedClass);

		/** 如果被被注解定义Bean，配置@Condition注解，判断条件是否满足，如果不满足直接返回 **/
		if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
			return;
		}

		/** 设置Supplier，如果设置成功createBeanInstance方法内部将优先使用Supplier创建bean实例**/
		abd.setInstanceSupplier(supplier);

		/** 如果被被注解定义Bean，配置@Scope注解，使用scopeMetadataResolver解析获取作用域设置到BeanDefinition
		 * 1 从BeanDefinition获取注解Bean,注解元数据对象AnnotationMetadata
		 * 2 通过解析注解元数据对象AnnotationMetadata，获取@Scope注解作用域元数据对象ScopeMetadata
		 * 3 读取元数据对象ScopeMetadata中Bean作用域设置到BeanDefinition**/
		ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
		abd.setScope(scopeMetadata.getScopeName());

		/** 使用beanNameGenerator组件生成Bean名称 **/
		String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

		/** 处理注解定义Bean配置通用注解，其中包括
		 * @Lazy，@DependsOn，@Role，@Description
		 * 1 从BeanDefinition获取注解Bean,注解元数据对象AnnotationMetadata
		 * 2 通过AnnotationConfigUtils.attributesFor 获取指定注解的属性
		 * 3 设置属性值到对应BeanDefinition属性 **/
		AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);

		/** 如果参数qualifiers注解不为NULL **/
		if (qualifiers != null) {
			for (Class<? extends Annotation> qualifier : qualifiers) {
				/** 如果配置了@Primary注解，设置该Bean自动依赖注入装配时的作为优先首选 **/
				if (Primary.class == qualifier) {
					abd.setPrimary(true);
				}
				/** 如果配置了@Lazy注解，则设置该Bean为延迟初始化,否则则该Bean在刷新时实例化 **/
				else if (Lazy.class == qualifier) {
					abd.setLazyInit(true);
				}
				/** 除了@Primary和@Lazy以外的其他注解，则为该Bean添加一 个autowiring自动依赖注入装配限定符 **/
				else {
					abd.addQualifier(new AutowireCandidateQualifier(qualifier));
				}
			}
		}
		/** 如果customizers不为NULL,执行定制化处理 **/
		if (customizers != null) {
			for (BeanDefinitionCustomizer customizer : customizers) {
				customizer.customize(abd);
			}
		}

		/** 将AnnotatedGenericBeanDefinition 封装成BeanDefinitionHolder， **/
		/** BeanDefinitionHolder 是对BeanDefinition 功能的扩展，增加了别名的功能**/
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);

		/** 根据bean是否作用域代理选项，创建BeanDefinitionHolder相应的代理BeanDefinitionHolder 对象  **/
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);

		/** 将@Configuration注解Bean BeanDefinitionHolder 注册到BeanFactory中 **/
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
	}


	/**
	 * 从BeanDefinitionRegistry获取环境配置组件Environment
	 */
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		return new StandardEnvironment();
	}

}
