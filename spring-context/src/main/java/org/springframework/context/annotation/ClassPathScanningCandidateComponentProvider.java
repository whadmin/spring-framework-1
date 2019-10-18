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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.index.CandidateComponentsIndex;
import org.springframework.context.index.CandidateComponentsIndexLoader;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Indexed;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A component provider that provides candidate components from a base package. Can
 * use {@link CandidateComponentsIndex the index} if it is available of scans the
 * classpath otherwise. Candidate components are identified by applying exclude and
 * include filters. {@link AnnotationTypeFilter}, {@link AssignableTypeFilter} include
 * filters on an annotation/superclass that are annotated with {@link Indexed} are
 * supported: if any other include filter is specified, the index is ignored and
 * classpath scanning is used instead.
 *
 * <p>This implementation is based on Spring's
 * {@link org.springframework.core.type.classreading.MetadataReader MetadataReader}
 * facility, backed by an ASM {@link org.springframework.asm.ClassReader ClassReader}.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 2.5
 * @see org.springframework.core.type.classreading.MetadataReaderFactory
 * @see org.springframework.core.type.AnnotationMetadata
 * @see ScannedGenericBeanDefinition
 * @see CandidateComponentsIndex
 */
public class ClassPathScanningCandidateComponentProvider implements EnvironmentCapable, ResourceLoaderAware {

	static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";


	/** 日志  */
	protected final Log logger = LogFactory.getLog(getClass());

	/** 默认扫描包路后缀   */
	private String resourcePattern = DEFAULT_RESOURCE_PATTERN;

	/** 包含类型过滤器 */
	private final List<TypeFilter> includeFilters = new LinkedList<>();

	/** 排除类型过滤器 */
	private final List<TypeFilter> excludeFilters = new LinkedList<>();

	/** 环境配置组件   */
	@Nullable
	private Environment environment;

	/** 条件处理器 处理{@link Condition}注解，*/
	@Nullable
	private ConditionEvaluator conditionEvaluator;

	/** 资源模式解析器，将指定资源位置路径解析为一个或多个匹配资源 */
	@Nullable
	private ResourcePatternResolver resourcePatternResolver;

	/** MetadataReader工厂，MetadataReader用来读取元数据 */
	@Nullable
	private MetadataReaderFactory metadataReaderFactory;

	/** 候选组件索引，用来加快检索Bean */
	@Nullable
	private CandidateComponentsIndex componentsIndex;


	/**
	 * 受保护的构造函数，用于灵活的子类初始化。
	 */
	protected ClassPathScanningCandidateComponentProvider() {
	}


	public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters) {
		this(useDefaultFilters, new StandardEnvironment());
	}


	/**
	 * 实例化一个新的ClassPathScanningCandidateComponentProvide
	 * @param useDefaultFilters  指定是否使用默认TypeFilter
	 * @param environment        指定环境配置
	 */
	public ClassPathScanningCandidateComponentProvider(boolean useDefaultFilters, Environment environment) {
		/** 如果使用默认TypeFilter，将默认TypeFilter设置到includeFilters **/
		if (useDefaultFilters) {
			registerDefaultFilters();
		}
		/** 设置环境配置 */
		setEnvironment(environment);
		/** 设置资源加载器 */
		setResourceLoader(null);
	}


	/**
	 * 设置默认扫描包路后缀
	 * @see #findCandidateComponents(String)
	 * @see #DEFAULT_RESOURCE_PATTERN
	 */
	public void setResourcePattern(String resourcePattern) {
		Assert.notNull(resourcePattern, "'resourcePattern' must not be null");
		this.resourcePattern = resourcePattern;
	}

	/**
	 * 注册一个包含TypeFilter
	 */
	public void addIncludeFilter(TypeFilter includeFilter) {
		this.includeFilters.add(includeFilter);
	}

	/**
	 * 注册一个排除TypeFilter
	 */
	public void addExcludeFilter(TypeFilter excludeFilter) {
		this.excludeFilters.add(0, excludeFilter);
	}

	/**
	 * 重置包含TypeFilter列表，排除TypeFilter列表
	 */
	public void resetFilters(boolean useDefaultFilters) {
		this.includeFilters.clear();
		this.excludeFilters.clear();
		if (useDefaultFilters) {
			registerDefaultFilters();
		}
	}

	/**
	 * 注册默认包含类型过滤器
	 */
	@SuppressWarnings("unchecked")
	protected void registerDefaultFilters() {
		this.includeFilters.add(new AnnotationTypeFilter(Component.class));
		ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();
		try {
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("javax.annotation.ManagedBean", cl)), false));
			logger.trace("JSR-250 'javax.annotation.ManagedBean' found and supported for component scanning");
		}
		catch (ClassNotFoundException ex) {
		}
		try {
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Named", cl)), false));
			logger.trace("JSR-330 'javax.inject.Named' annotation found and supported for component scanning");
		}
		catch (ClassNotFoundException ex) {
		}
	}

	/**
	 * 设置环境配置
	 */
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
		this.conditionEvaluator = null;
	}

	/**
	 * 返回环境配置，如果不存在设置成StandardEnvironment
	 */
	@Override
	public final Environment getEnvironment() {
		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}
		return this.environment;
	}

	/**
	 * 返回此扫描器使用的{@link BeanDefinitionRegistry}（如果有）。
	 */
	@Nullable
	protected BeanDefinitionRegistry getRegistry() {
		return null;
	}

	/**
	 * 设置资源加载器
	 */
	@Override
	public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
		this.resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
		this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		this.componentsIndex = CandidateComponentsIndexLoader.loadIndex(this.resourcePatternResolver.getClassLoader());
	}

	/**
	 *返回资源加载器，如果不存在设置成PathMatchingResourcePatternResolver
	 */
	public final ResourceLoader getResourceLoader() {
		return getResourcePatternResolver();
	}

	private ResourcePatternResolver getResourcePatternResolver() {
		if (this.resourcePatternResolver == null) {
			this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
		}
		return this.resourcePatternResolver;
	}

	/**
	 * 设置使用{@link MetadataReaderFactory}。
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		this.metadataReaderFactory = metadataReaderFactory;
	}

	/**
	 * 返回此组件提供者使用的MetadataReaderFactory。
	 */
	public final MetadataReaderFactory getMetadataReaderFactory() {
		if (this.metadataReaderFactory == null) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory();
		}
		return this.metadataReaderFactory;
	}


	/**
	 * 扫描类路径以查找候选Bean。
	 */
	public Set<BeanDefinition> findCandidateComponents(String basePackage) {

		if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
			return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
		}
		/** 扫描类路径以查找候选Bean。 **/
		else {
			return scanCandidateComponents(basePackage);
		}
	}

	/**
	 * 确定Indexed支持所有includeFilters
	 */
	private boolean indexSupportsIncludeFilters() {
		for (TypeFilter includeFilter : this.includeFilters) {
			if (!indexSupportsIncludeFilter(includeFilter)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 确定Indexed是否支持指定includeFilters
	 */
	private boolean indexSupportsIncludeFilter(TypeFilter filter) {
		if (filter instanceof AnnotationTypeFilter) {
			Class<? extends Annotation> annotation = ((AnnotationTypeFilter) filter).getAnnotationType();
			return (AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, annotation) ||
					annotation.getName().startsWith("javax."));
		}
		if (filter instanceof AssignableTypeFilter) {
			Class<?> target = ((AssignableTypeFilter) filter).getTargetType();
			return AnnotationUtils.isAnnotationDeclaredLocally(Indexed.class, target);
		}
		return false;
	}


	@Nullable
	private String extractStereotype(TypeFilter filter) {
		if (filter instanceof AnnotationTypeFilter) {
			return ((AnnotationTypeFilter) filter).getAnnotationType().getName();
		}
		if (filter instanceof AssignableTypeFilter) {
			return ((AssignableTypeFilter) filter).getTargetType().getName();
		}
		return null;
	}

	private Set<BeanDefinition> addCandidateComponentsFromIndex(CandidateComponentsIndex index, String basePackage) {
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			Set<String> types = new HashSet<>();
			for (TypeFilter filter : this.includeFilters) {
				String stereotype = extractStereotype(filter);
				if (stereotype == null) {
					throw new IllegalArgumentException("Failed to extract stereotype from " + filter);
				}
				types.addAll(index.getCandidateTypes(basePackage, stereotype));
			}
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
			for (String type : types) {
				MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(type);
				if (isCandidateComponent(metadataReader)) {
					AnnotatedGenericBeanDefinition sbd = new AnnotatedGenericBeanDefinition(
							metadataReader.getAnnotationMetadata());
					if (isCandidateComponent(sbd)) {
						if (debugEnabled) {
							logger.debug("Using candidate component class from index: " + type);
						}
						candidates.add(sbd);
					}
					else {
						if (debugEnabled) {
							logger.debug("Ignored because not a concrete top-level class: " + type);
						}
					}
				}
				else {
					if (traceEnabled) {
						logger.trace("Ignored because matching an exclude filter: " + type);
					}
				}
			}
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		return candidates;
	}

	/**
	 * 扫描指定包路径将匹配Bean定义集合返回
	 */
	private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			/** 解析包路径为URL 路径  classpath*: ...**/
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
					resolveBasePackage(basePackage) + '/' + this.resourcePattern;
			/** 通过resourcePatternResolver 将指定资源位置路径解析为一个或多个匹配资源 **/
			Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
			/** 遍历资源 **/
			for (Resource resource : resources) {
				if (traceEnabled) {
					logger.trace("Scanning " + resource);
				}
				/** 判断资源是否可读 **/
				if (resource.isReadable()) {
					try {
						/** 获取MetadataReader **/
						MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
						/** 确定给定的类是否不匹配任何excludeFilters并且是否匹配至少一个includeFilters。 **/
						if (isCandidateComponent(metadataReader)) {
							/** 如果满足实例化 ScannedGenericBeanDefinition 添加到candidates **/
							ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
							sbd.setResource(resource);
							sbd.setSource(resource);
							if (isCandidateComponent(sbd)) {
								if (debugEnabled) {
									logger.debug("Identified candidate component class: " + resource);
								}
								candidates.add(sbd);
							}
							else {
								if (debugEnabled) {
									logger.debug("Ignored because not a concrete top-level class: " + resource);
								}
							}
						}
						else {
							if (traceEnabled) {
								logger.trace("Ignored because not matching any filter: " + resource);
							}
						}
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to read candidate component class: " + resource, ex);
					}
				}
				else {
					if (traceEnabled) {
						logger.trace("Ignored because not readable: " + resource);
					}
				}
			}
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		/** 返回candidates **/
		return candidates;
	}


	/**
	 * 将指定的基本包路径解析为URL路径
	 */
	protected String resolveBasePackage(String basePackage) {
		return ClassUtils.convertClassNameToResourcePath(getEnvironment().resolveRequiredPlaceholders(basePackage));
	}

	/**
	 * 确定给定的类是否不匹配任何excludeFilters并且是否匹配至少一个includeFilters。
	 * @return 该类是否符合候选资格
	 */
	protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
		for (TypeFilter tf : this.excludeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return false;
			}
		}
		for (TypeFilter tf : this.includeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return isConditionMatch(metadataReader);
			}
		}
		return false;
	}

	/**
	 * Determine whether the given class is a candidate component based on any
	 * {@code @Conditional} annotations.
	 * @param metadataReader the ASM ClassReader for the class
	 * @return whether the class qualifies as a candidate component
	 */
	private boolean isConditionMatch(MetadataReader metadataReader) {
		if (this.conditionEvaluator == null) {
			this.conditionEvaluator =
					new ConditionEvaluator(getRegistry(), this.environment, this.resourcePatternResolver);
		}
		return !this.conditionEvaluator.shouldSkip(metadataReader.getAnnotationMetadata());
	}

	/**
	 * Determine whether the given bean definition qualifies as candidate.
	 * <p>The default implementation checks whether the class is not an interface
	 * and not dependent on an enclosing class.
	 * <p>Can be overridden in subclasses.
	 * @param beanDefinition the bean definition to check
	 * @return whether the bean definition qualifies as a candidate component
	 */
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		AnnotationMetadata metadata = beanDefinition.getMetadata();
		return (metadata.isIndependent() && (metadata.isConcrete() ||
				(metadata.isAbstract() && metadata.hasAnnotatedMethods(Lookup.class.getName()))));
	}


	/**
	 * 清理CachingMetadataReaderFactory 中MetadataReader缓存
	 */
	public void clearCache() {
		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

}
