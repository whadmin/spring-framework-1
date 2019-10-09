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

package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.springframework.lang.Nullable;

/**
 * 资源描述符的接口，它从基础资源的实际类型中抽象出来，
 */
public interface Resource extends InputStreamSource {

	/**
	 * 返回当前资源是否存在
	 */
	boolean exists();

	/**
	 * 返回当前资源是否可读
	 */
	default boolean isReadable() {
		return exists();
	}

	/**
	 * 返回当前资源是否已经打开
	 */
	default boolean isOpen() {
		return false;
	}

	/**
	 * 确定此资源是否代表文件系统中的文件
	 */
	default boolean isFile() {
		return false;
	}

	/**
	 * 返回此资源的URL句柄。如果资源不能解析为URL将抛出 @throws IOException
	 */
	URL getURL() throws IOException;

	/**
	 * 返回此资源的URI句柄。如果资源不能解析为URL将抛出 @throws IOException
	 */
	URI getURI() throws IOException;

	/**
	 * 返回此资源的文件句柄。
	 * @throws java.io.FileNotFoundException 如果资源不能解析为绝对文件路径，即资源在文件系统中不可用
	 * @throws IOException 如果出现一般分辨率/读取失败
	 */
	File getFile() throws IOException;

	/**
	 * 返回ReadableByteChannel。
	 */
	default ReadableByteChannel readableChannel() throws IOException {
		return Channels.newChannel(getInputStream());
	}

	/**
	 * 返回此资源的内容长度
	 */
	long contentLength() throws IOException;

	/**
	 * 返回资源的最后修改的时间戳
	 */
	long lastModified() throws IOException;

	/**
	 * 根据相对路径（相对于此资源）创建相对于该资源的资源。
	 * @param relativePath 相对路径（相对于此资源）
	 * @throws IOException 如果无法确定相对资源
	 */
	Resource createRelative(String relativePath) throws IOException;

	/**
	 * 返回该资源的文件名
	 */
	@Nullable
	String getFilename();

	/**
	 * 返回资源的描述
	 */
	String getDescription();

}
