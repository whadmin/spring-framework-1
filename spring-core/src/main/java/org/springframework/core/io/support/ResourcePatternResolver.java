/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * 资源模式解析器，将指定资源位置路径解析为一个或多个匹配资源
 */
public interface ResourcePatternResolver extends ResourceLoader {

	/**
	 * 类路径中所有匹配资源URL前缀
	 */
	String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

	/**
	 * 将指定资源位置路径解析为一个或多个匹配资源
	 */
	Resource[] getResources(String locationPattern) throws IOException;

}
