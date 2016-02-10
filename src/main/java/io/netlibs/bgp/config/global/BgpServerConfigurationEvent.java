/**
 *  Copyright 2012 Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * File: org.bgp4.config.global.BgpServerConfigurationEvent.java 
 */
package io.netlibs.bgp.config.global;

import io.netlibs.bgp.config.nodes.BgpServerConfiguration;

/**
 * @author Rainer Bieniek (Rainer.Bieniek@web.de)
 *
 */
public class BgpServerConfigurationEvent extends TypedEvent {

	private BgpServerConfiguration configuration;
	
	public BgpServerConfigurationEvent(EventType type, BgpServerConfiguration configuration) {
		super(type);
		this.configuration = configuration;
	}

	/**
	 * @return the configuration
	 */
	public BgpServerConfiguration getConfiguration() {
		return configuration;
	}
}
