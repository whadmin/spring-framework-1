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

package org.springframework.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * {@link AliasRegistry}接口的简单实现。, 作为基础类
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	protected final Log logger = LogFactory.getLog(getClass());

	/** 从别名 -- 规范名称 映射 */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);


	/**
	 * 注册别名
	 */
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			}
			else {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					if (registeredName.equals(name)) {
						return;
					}
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}
				checkForAliasCircle(name, alias);
				this.aliasMap.put(alias, name);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * 返回是否允许别名覆盖。默认值为{@code true}。
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}


	/**
	 * 确定给定名称是否已注册给定别名（会递归检查规范名称作为别名是否注册给定别名）
	 *
	 * registry.registerAlias("test", "testAlias");
	 * registry.registerAlias("testAlias", "testAlias2");
	 * registry.registerAlias("testAlias2", "testAlias3");
	 *
	 * assertThat(registry.hasAlias("test", "testAlias")).isTrue();
	 * assertThat(registry.hasAlias("test", "testAlias2")).isTrue();
	 * assertThat(registry.hasAlias("test", "testAlias3")).isTrue();
	 */
	public boolean hasAlias(String name, String alias) {
		for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
			String registeredName = entry.getValue();
			if (registeredName.equals(name)) {
				String registeredAlias = entry.getKey();
				/** 递归规范名称作为别名的时是否存在此别名**/
				if (registeredAlias.equals(alias) || hasAlias(registeredAlias, alias)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 删除别名
	 */
	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * 是否存在此别名
	 */
	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}


	/**
	 * 返回名称对应所有别名
	 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * 获取给定名称的所有别名
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				/** 执行递归，获取别名作为规范名称 对应的别名 **/
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * 通过StringValueResolver解析所有别名和规则名称
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			/** 遍历别名 -- 规范名称映射 **/
			aliasCopy.forEach((alias, registeredName) -> {
				/** 通过StringValueResolver解析器解析别名，规范名称  **/
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				String resolvedName = valueResolver.resolveStringValue(registeredName);

				/** 解析后别名和规则名称满足下面条件从 映射中删除 **/
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				}
				/** 解析后别名和原始别名不相同 **/
				else if (!resolvedAlias.equals(alias)) {
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							return;
						}
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
								"') for name '" + resolvedName + "': It is already registered for name '" +
								registeredName + "'.");
					}
					checkForAliasCircle(resolvedName, resolvedAlias);
					this.aliasMap.remove(alias);
					this.aliasMap.put(resolvedAlias, resolvedName);
				}
				/** 解析后规则名称和原始规范名称不相同 **/
				else if (!registeredName.equals(resolvedName)) {
					this.aliasMap.put(alias, resolvedName);
				}
			});
		}
	}

	/**
	 * 检查给定名称是否已注册给定别名（会递归检查规范名称作为别名是否注册给定别名），如果存在抛出异常
	 */
	protected void checkForAliasCircle(String name, String alias) {
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * 获取name作为别名对应bean名称（会递归调用规范名称作为别名）
	 *
	 * registry.registerAlias("test", "testAlias");
	 * registry.registerAlias("testAlias", "testAlias2");
	 * registry.registerAlias("testAlias2", "testAlias3");
	 *
	 * assertThat(registry.canonicalName("testAlias")).isSameAs("test");
	 * assertThat(registry.canonicalName("testAlias2")).isSameAs("test");
	 * assertThat(registry.canonicalName("testAlias3")).isSameAs("test");
	 */
	public String canonicalName(String name) {
		String canonicalName = name;
		String resolvedName;
		do {
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				canonicalName = resolvedName;
			}
		}
		while (resolvedName != null);
		return canonicalName;
	}

}
