/*
 * Copyright 2015 Giiwa, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.core.cache.impl;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.*;
import org.giiwa.core.cache.Cachable;
import org.giiwa.core.cache.Cache;
import org.giiwa.core.cache.ICacheSystem;

import com.danga.MemCached.MemCachedClient;
import com.danga.MemCached.SockIOPool;

// TODO: Auto-generated Javadoc
/**
 * The Class MemCache is used to memcached cache <br>
 * url: memcached://host:port
 */
public class MemCache implements ICacheSystem {

	/** The log. */
	static Log log = LogFactory.getLog(MemCache.class);

	private MemCachedClient memCachedClient;

	/**
   * Inits the.
   *
   * @param conf
   *          the conf
   * @return the i cache system
   */
	public static ICacheSystem create(Configuration conf) {
		String server = conf.getString("cache.url");

		MemCache f = new MemCache();

		SockIOPool pool = SockIOPool.getInstance();
		pool.setServers(new String[] { server.substring(Cache.MEMCACHED.length()) });
		pool.setFailover(true);
		pool.setInitConn(10);
		pool.setMinConn(5);
		pool.setMaxConn(1000);
		pool.setMaintSleep(30);
		pool.setNagle(false);
		pool.setSocketTO(3000);
		pool.setAliveCheck(true);
		pool.initialize();

		f.memCachedClient = new MemCachedClient();

		return f;
	}

	/**
	 * get object.
	 *
	 * @param id
	 *            the id
	 * @return the object
	 */
	public synchronized Cachable get(String id) {
		return (Cachable) memCachedClient.get(id);
	}

	/**
	 * Sets the.
	 *
	 * @param id
	 *            the id
	 * @param o
	 *            the o
	 * @return true, if successful
	 */
	public synchronized boolean set(String id, Cachable o) {
		try {
			if (o == null) {
				return delete(id);
			} else {
				return memCachedClient.set(id, o);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return false;
	}

	/**
	 * Delete.
	 *
	 * @param id
	 *            the id
	 * @return true, if successful
	 */
	public synchronized boolean delete(String id) {
		return memCachedClient.delete(id);
	}

}
