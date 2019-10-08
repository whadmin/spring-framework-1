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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	/**
	 * 执行BeanFactoryPostProcessor.postProcessBeanFactory方法
	 * 1 优先执行参数beanFactoryPostProcessors列表对象postProcessBeanFactory方法
	 *   1.1 如果beanFactory 类型是 BeanDefinitionRegistry
	 *       1.1.1 从beanFactory获取类型为BeanDefinitionRegistryPostProcessor bean，按照是否实现的PriorityOrdered接口，Ordered接口归类为3个列表，分别执行
	 *                 优先处理类型为 PriorityOrdered bean，执行postProcessBeanDefinitionRegistry
	 *                 接着处理类型为 Ordered bean，执行postProcessBeanDefinitionRegistry
	 *                 最后处理类型非Ordered，PriorityOrdered bean，执行postProcessBeanDefinitionRegistry
	 *       1.1.2 将参数参数beanFactoryPostProcessors列表中对象，按照是否类型为BeanDefinitionRegistryPostProcessor，归类为2个列表分别执行
	 *                 优先处理类型为 BeanDefinitionRegistryPostProcessor bean,执行postProcessBeanFactory(beanFactory)
	 *                 接着处理类型非 BeanDefinitionRegistryPostProcessor bean,执行postProcessBeanFactory(beanFactory)
	 *   1.2 如果beanFactory 类型非 BeanDefinitionRegistry
	 *         执行参数beanFactoryPostProcessors列表中对象postProcessBeanFactory(beanFactory)方法
	 * 2 从beanFactory获取类型为BeanFactoryPostProcessor bean，按照是否实现PriorityOrdered接口，Ordered接口归类为3个列表，分别执行
	 *           优先处理类型为 PriorityOrdered bean，执行postProcessBeanFactory
	 * 	         接着处理类型为 Ordered bean，执行postProcessBeanFactory
	 * 	         最后处理类型非Ordered，PriorityOrdered bean，执行postProcessBeanFactory
	 * 3 最后执行清理beanFactory.clearMetadataCache();
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		/** 保存已经执行BeanFactoryPostProcessor bean名称 **/
		Set<String> processedBeans = new HashSet<>();

		/** beanFactory 类型是 BeanDefinitionRegistry（实现BeanDefinitionRegistry接口） **/
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			/**
			 * 将beanFactoryPostProcessors按照是否实现BeanDefinitionRegistryPostProcessor，
			 * 划分为regularPostProcessors，registryProcessors列表 **/

			//定义列表存储参数beanFactoryPostProcessors中类型非BeanDefinitionRegistryPostProcessor **/
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();

			// 定义列表存储参数beanFactoryPostProcessors中类型为BeanDefinitionRegistryPostProcessor **/
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 遍历beanFactoryPostProcessors，按照是否实现BeanDefinitionRegistryPostProcessor 分布添加到regularPostProcessors，registryProcessors列表 **/
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			/**
			 * 从beanFactory获取类型为BeanDefinitionRegistryPostProcessor，PriorityOrdered bean，
			 * 执行postProcessBeanDefinitionRegistry
			 **/
			//定义列表存储从beanFactory获取类型为BeanDefinitionRegistryPostProcessor bean实例
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            //从beanFactory获取实现BeanDefinitionRegistryPostProcessor，PriorityOrdered bean实例，
			//将其名称添加到processedBeans，将bean实例添加到currentRegistryProcessors
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			//对列表postProcessors对象排序，默认规则OrderComparator.INSTANCE，
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//将currentRegistryProcessors 添加到registryProcessors
			registryProcessors.addAll(currentRegistryProcessors);
			//执行currentRegistryProcessors.postProcessBeanDefinitionRegistry Bean列表
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//清理currentRegistryProcessors
			currentRegistryProcessors.clear();

			/**
			 * 从beanFactory获取类型为BeanDefinitionRegistryPostProcessor，Ordered bean，
			 * 执行postProcessBeanDefinitionRegistry
			 **/
			// 下一步, //从beanFactory获取实现BeanDefinitionRegistryPostProcessor，Ordered bean实例，
			//将其名称添加到processedBeans，将bean实例添加到currentRegistryProcessors
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}

			//对列表postProcessors对象排序，默认规则OrderComparator.INSTANCE，
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//将currentRegistryProcessors 添加到registryProcessors
			registryProcessors.addAll(currentRegistryProcessors);
			//执行currentRegistryProcessors.postProcessBeanDefinitionRegistry Bean列表
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//清理currentRegistryProcessors
			currentRegistryProcessors.clear();


			/**
			 * 从beanFactory获取类型为BeanDefinitionRegistryPostProcessor，非PriorityOrdered，Ordered bean，
			 * 执行postProcessBeanDefinitionRegistry
			 **/
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				//从beanFactory获取实现BeanDefinitionRegistryPostProcessor， bean名称
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				//遍历postProcessorNames，过滤掉已经执行的（类型为Ordered，PriorityOrdered）
				//将其名称添加到processedBeans，将bean实例添加到currentRegistryProcessors
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				//对列表postProcessors对象排序，默认规则OrderComparator.INSTANCE，
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				//将currentRegistryProcessors 添加到registryProcessors
				registryProcessors.addAll(currentRegistryProcessors);
				//执行currentRegistryProcessors.postProcessBeanDefinitionRegistry Bean列表
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				//清理currentRegistryProcessors
				currentRegistryProcessors.clear();
			}

			/**  遍历registryProcessors列表中所有BeanFactoryPostProcessor，执行postProcessBeanFactory(beanFactory) **/
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			/**  遍历regularPostProcessors列表中所有BeanFactoryPostProcessor，执行postProcessBeanFactory(beanFactory) **/
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}
		/** beanFactory 类型不是BeanDefinitionRegistry（实现BeanDefinitionRegistry接口） **/
		else {
			/**  遍历参数beanFactoryPostProcessors列表中所有BeanFactoryPostProcessor，执行postProcessBeanFactory(beanFactory) **/
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		//从beanFactory获取实现BeanFactoryPostProcessor， bean名称
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		//定义列表存储参数beanFactoryPostProcessors中类型为PriorityOrdered，且未处理的 **/
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//定义列表存储参数beanFactoryPostProcessors中类型为Ordered，且未处理的 **/
		List<String> orderedPostProcessorNames = new ArrayList<>();
		//定义列表存储参数beanFactoryPostProcessors中类型非Ordered，PriorityOrdered 且未处理的 **/
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		//遍历postProcessorNames，将其其中bean归类到priorityOrderedPostProcessors，
		//orderedPostProcessorNames，nonOrderedPostProcessorNames三个列表中
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}
        /** 优先 执行priorityOrderedPostProcessors对应BeanFactoryPostProcessor **/
		//对列表priorityOrderedPostProcessors对象排序，默认规则OrderComparator.INSTANCE，
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		//遍历执行BeanFactoryPostProcessor列表对象postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		/**  执行orderedPostProcessorNames名称对应BeanFactoryPostProcessor **/

		// 获取postProcessorName列表中bean名称对应bean实例，添加到orderedPostProcessors
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 对列表orderedPostProcessors对象排序，默认规则OrderComparator.INSTANCE，
		sortPostProcessors(orderedPostProcessors, beanFactory);
		//遍历执行orderedPostProcessors列表对象postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);


		/**  执行nonOrderedPostProcessorNames名称对应BeanFactoryPostProcessor **/

		// 获取nonOrderedPostProcessors列表中bean名称对应bean实例，添加到nonOrderedPostProcessors
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//遍历执行orderedPostProcessors列表对象postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// 清理merged bean definition
		beanFactory.clearMetadataCache();
	}

	/**
	 * 将BeanPostProcessor注册到beanFactory
	 * 1 注册BeanPostProcessor接口实现BeanPostProcessorChecker给beanFactory
	 *
	 * 2 从beanFactory获取类型为BeanPostProcessor bean，按照是否实现PriorityOrdered接口，Ordered接口,MergedBeanDefinitionPostProcessor接口归类为4个列表
		 * 	 优先处理类型为 PriorityOrdered bean对象，首先将其排序，之后注册到beanFactory
		 * 	 接着处理类型为 Ordered bean对象，首先将其排序，之后注册到beanFactory
		 * 	 接着处理类型非 Ordered，PriorityOrdered bean，注册到beanFactory
		 * 	 最后处理类为为 PriorityOrdered MergedBeanDefinitionPostProcessor 首先将其排序，之后注册到beanFactory
	 * 3 注册BeanPostProcessor接口实现ApplicationListenerDetector给beanFactory
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		/** 从 beanFactory获取实现BeanPostProcessor， bean名称 **/
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		/** 注册BeanPostProcessor接口实现BeanPostProcessorChecker给beanFactory **/
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));


		/** 定义列表存储类型为PriorityOrdered，BeanPostProcessor对象 **/
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		/** 定义列表存储类型为PriorityOrdered，MergedBeanDefinitionPostProcessor BeanPostProcessor对象 **/
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		/** 定义列表存储类型为Ordered BeanPostProcessor对象 **/
		List<String> orderedPostProcessorNames = new ArrayList<>();
		/** 定义列表存储类型非PriorityOrdered,Ordered BeanPostProcessor对象 **/
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		/** 遍历postProcessorNames，按照是否实现PriorityOrdered接口，Ordered接口,MergedBeanDefinitionPostProcessor接口，归类为4个列表 **/
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		/** 对列表priorityOrderedPostProcessors对象排序，默认规则OrderComparator.INSTANCE， **/
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		/** 将priorityOrderedPostProcessors bean对象注册到beanFactory **/
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		/** 获取orderedPostProcessorNames列表中bean名称对应bean实例，添加到orderedPostProcessors **/
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		/** 对列表orderedPostProcessors对象排序，默认规则OrderComparator.INSTANCE， **/
		sortPostProcessors(orderedPostProcessors, beanFactory);
		/** 将orderedPostProcessors bean对象注册到beanFactory **/
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		/** 获取nonOrderedPostProcessorNames列表中bean名称对应bean实例，添加到nonOrderedPostProcessors **/
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		/** 将nonOrderedPostProcessors bean对象注册到beanFactory **/
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);



		/** 对列表internalPostProcessors对象排序，默认规则OrderComparator.INSTANCE， **/
		sortPostProcessors(internalPostProcessors, beanFactory);
		/** 将internalPostProcessors bean对象注册到beanFactory **/
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		/** 注册BeanPostProcessor接口实现ApplicationListenerDetector给beanFactory **/
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	/**
	 * 对列表postProcessors对象排序，默认规则OrderComparator.INSTANCE，
	 * 可以通过beanFactory.setDependencyComparator(@Nullable Comparator<Object> dependencyComparator)手动设置排序规则
	 */
	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * 遍历执行BeanDefinitionRegistryPostProcessor列表对象postProcessBeanDefinitionRegistry方法
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * 遍历执行BeanFactoryPostProcessor列表对象postProcessBeanFactory方法
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * 将给定 BeanPostProcessor bean对象注册到beanFactory
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
