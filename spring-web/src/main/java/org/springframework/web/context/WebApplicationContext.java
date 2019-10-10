/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.context;

import javax.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

/**
 */
public interface WebApplicationContext extends ApplicationContext {

	/**
	 * 成功启动时将根WebApplicationContext绑定到的Context属性。
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#getWebApplicationContext
	 * @see org.springframework.web.context.support.WebApplicationContextUtils#getRequiredWebApplicationContext
	 */
	String ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE = WebApplicationContext.class.getName() + ".ROOT";

	/**
	 * "request"作用域
	 */
	String SCOPE_REQUEST = "request";

	/**
	 * "session"作用域
	 */
	String SCOPE_SESSION = "session";

	/**
	 * "application"作用域
	 */
	String SCOPE_APPLICATION = "application";

	/**
	 * 将servletContext 作为bean对象注册到BeanFactory名称
	 */
	String SERVLET_CONTEXT_BEAN_NAME = "servletContext";

	/**
	 * 将servletContext init-params（初始化参数）注册到BeanFactory名称
	 * @see javax.servlet.ServletContext#getInitParameterNames()
	 * @see javax.servlet.ServletContext#getInitParameter(String)
	 * @see javax.servlet.ServletConfig#getInitParameterNames()
	 * @see javax.servlet.ServletConfig#getInitParameter(String)
	 */
	String CONTEXT_PARAMETERS_BEAN_NAME = "contextParameters";

	/**
	 * 将servletContext attribute（属性）注册到BeanFactory名称
	 * @see javax.servlet.ServletContext#getAttributeNames()
	 * @see javax.servlet.ServletContext#getAttribute(String)
	 */
	String CONTEXT_ATTRIBUTES_BEAN_NAME = "contextAttributes";


	/**
	 * 返回此应用程序的标准Servlet API ServletContext。
	 */
	@Nullable
	ServletContext getServletContext();

}
