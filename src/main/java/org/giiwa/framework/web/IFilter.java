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
package org.giiwa.framework.web;

/**
 * the Module Filter for the url
 * 
 * @author wujun
 *
 */
public interface IFilter {

  /**
   * before dispatch to the model
   * 
   * @param m
   *          the model
   * @return boolean, true = pass to the model, pass to the next "filter"; false
   *         = stop pass to the model, and stop pass to the next "filter"
   */
  boolean before(Model m);

  /**
   * after dispatched to the model
   * 
   * @param m
   *          the model
   * @return boolean, true = pass to the next "filter", false = stop the pass to
   *         the next "filter"
   */
  boolean after(Model m);

}
