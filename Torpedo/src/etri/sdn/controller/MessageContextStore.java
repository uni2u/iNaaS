/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package etri.sdn.controller;

/**
 * This class is no longer used in Torpedo. 
 * Most of the function is now integrated with MessageContext.
 * Originally, this class was FloodlightContextStore in Floodlight.
 * 
 * @deprecated
 * @author bjlee
 *
 * @param <V> type of the object that associated with the given key value
 */
public final class MessageContextStore<V> {
    
    @SuppressWarnings("unchecked")
    public V get(MessageContext bc, String key) {
        return (V)bc.getStorage().get(key);
    }
    
    public void put(MessageContext bc, String key, V value) {
        bc.getStorage().put(key, value);
    }
    
    public void remove(MessageContext bc, String key) {
        bc.getStorage().remove(key);
    }
}
