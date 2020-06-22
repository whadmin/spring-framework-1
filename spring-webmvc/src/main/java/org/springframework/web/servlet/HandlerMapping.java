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

package org.springframework.web.servlet;

import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

/**
 * 定义请求URL和处理程序Handler对象之间的映射关系的对象实现的接口。
 *
 * 核心实现为
 * 1 {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping}
 * 2 {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}
 *
 * <p> HandlerMapping实现可以支持映射的拦截器，
 * 通过请求URL获取处理程序Handler被包装在{@link HandlerExecutionChain}实例中，并可选地伴随一些{@link HandlerInterceptor}实例。
 * DispatcherServlet将首先以给定的顺序调用每个HandlerInterceptor的{@code preHandle}方法，
 * 最后在满足以下条件时调用处理程序本身：所有{@code preHandle}方法都返回了{@code true}。
 *
 * <p>The ability to parameterize this mapping is a powerful and unusual
 * capability of this MVC framework. For example, it is possible to write
 * a custom mapping based on session state, cookie state or many other
 * variables. No other MVC framework seems to be equally flexible.
 *
 * HandlerMapping实现可以实现{@link org.springframework.core.Ordered}，
 * 这样当存在多个HandlerMapping实现实现时可以指定的优先级
 *
 */
public interface HandlerMapping {

	/**
	 * 存储最佳匹配配置请求程序 SupportExpressionController1,保存到request属性的key
	 *
	 * 对于请求/expression，如下配置请求URL正则都匹配
	 * <bean name="/expressio?" class="com.wuhao.web.SupportExpressionController1"/>
	 * <bean name="/expressio*" class="com.wuhao.web.SupportExpressionController2"/>
	 */
	String BEST_MATCHING_HANDLER_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingHandler";

	/**
	 * 存储最佳匹配配置请求URL /expressio?,保存到request属性的key
	 *
	 * 对于请求/expression，如下配置请求URL正则都匹配
	 * <bean name="/expressio?" class="com.wuhao.web.SupportExpressionController1"/>
	 * <bean name="/expressio*" class="com.wuhao.web.SupportExpressionController2"/>
	 *
	 */
	String BEST_MATCHING_PATTERN_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingPattern";


	/**
	 * 请求路径 = /hello 保存到request属性的key
	 * http请求 http://localhost:8080/beanNameUrlHandlerMapping_war/hello
	 */
	String LOOKUP_PATH = HandlerMapping.class.getName() + ".lookupPath";


	/**
	 * 请求路径正则匹配值 保存到request属性的key
	 * 参考 AntPathMatcherTests
	 * //assertThat(pathMatcher.extractPathWithinPattern("/docs/*", "/docs/cvs/commit")).isEqualTo("cvs/commit");
	 */
	String PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE = HandlerMapping.class.getName() + ".pathWithinHandlerMapping";


	/**
	 * 是否支持类型级别的映射,保存到request属性的key
	 */
	String INTROSPECT_TYPE_LEVEL_MAPPING = HandlerMapping.class.getName() + ".introspectTypeLevelMapping";


	/**
	 * 路径占位符Map，保存到request属性的key
	 * 参考 AntPathMatcherTests
	 * Map<String, String> result = pathMatcher.extractUriTemplateVariables("/hotels/{hotel}", "/hotels/1");
	 * assertThat(result).isEqualTo(Collections.singletonMap("hotel", "1"));
	 */
	String URI_TEMPLATE_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".uriTemplateVariables";


	String MATRIX_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".matrixVariables";


	String PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE = HandlerMapping.class.getName() + ".producibleMediaTypes";

	/**
	 * 1 通过请求{@link HttpServletRequest}获取匹配处理程序{@link Handler},
	 * 2 将处理程序{@link Handler}被包装在{@link HandlerExecutionChain}实例中，
	 * 3 {@link HandlerExecutionChain} 不仅包装了处理程序{@link Handler}，并管理多个{@link HandlerInterceptor}实例。
	 * 4 {@link DispatcherServlet}，在调用处理程序{@link Handler}前，以给定的顺序调用每个{@link HandlerInterceptor#preHandle}方法，判断是否能执行
	 * 5 {@link DispatcherServlet}，在调用处理程序{@link Handler}后，以给定的顺序调用每个{@link HandlerInterceptor#postHandle}方法
	 */
	@Nullable
	HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception;

}
