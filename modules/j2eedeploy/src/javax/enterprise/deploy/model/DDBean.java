/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package javax.enterprise.deploy.model;

/**
 * Represents a fragment of a deployment descriptor.
 */
public interface DDBean {
  /**
   * Returns the original XPath string.
   */
  public String getXpath();

  /**
   * Returns the bean's XML text.
   */
  public String getText();

  /**
   * Returns the attribute ID for the element.
   */
  public String getId();

  /**
   * Returns the root element.
   */
  public DDBeanRoot getRoot();

  /**
   * Returns the child beans based on the XPath.
   */
  public DDBean []getChildBean(String xpath);

  /**
   * Returns the list of text values for the XPath.
   */
  public String []getText(String xpath);

  /**
   * Registers a listener for the given xpath.
   */
  public void addXpathListener(String xpath, XpathListener xpl);

  /**
   * Unregisters a listener for the given xpath.
   */
  public void removeXpathListener(String xpath, XpathListener xpl);

  /**
   * Returns the list of attribute names for the XML.
   */
  public String []getAttributeNames();

  /**
   * Returns the value for an attribute.
   */
  public String getAttributeValue(String attrName);
}

