/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package javax.mail.search;
import javax.mail.*;

/**
 * This class implements comparisons for the Recipient Address headers.
 */
public final class RecipientTerm extends AddressTerm {

  private Message.RecipientType _type;

  public RecipientTerm(Message.RecipientType type, Address pattern)
  {
    super(pattern);

    this._type = type;
  }

  /**
   * Equality comparison.
   */
  public boolean equals(Object obj)
  {
    if (! (obj instanceof RecipientTerm))
      return false;

    RecipientTerm recipientStringTerm =
      (RecipientTerm)obj;
    
    if (! recipientStringTerm._type.equals(_type))
      return false;

    return super.equals(obj);
  }

  /**
   * Return the type of recipient to match with.
   */
  public Message.RecipientType getRecipientType()
  {
    return _type;
  }

  /**
   * Compute a hashCode for this object.
   */
  public int hashCode()
  {
    int hash = super.hashCode();

    hash = hash * 65521 + _type.hashCode();
    
    return hash;
  }

  /**
   * Check whether the address specified in the constructor is a
   * substring of the recipient address of this Message.
   */
  public boolean match(Message msg)
  {
    try {
      Address []recipients = msg.getRecipients(_type);
      
      for(int i=0; i<recipients.length; i++)
	if (match(recipients[i]))
	  return true;
      
      return false;

    }
    catch (MessagingException e) {
      throw new RuntimeException(e);
    }
  }

}
