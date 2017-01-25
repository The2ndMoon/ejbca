/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.roles.member;

import javax.ejb.Remote;

/**
 * @version $Id$
 *
 */
@Remote
public interface RoleMemberProxySessionRemote {

    /**
     * Saves a RoleMemberData object to the database, either overwriting an existing one with the same primary key or editing an existing instance.
     * 
     * @param accessUserAspectData the AccessUserAspectData to persist.
     */
    int createOrEdit(final RoleMember roleMember);
    
    /**
     * Saves a RoleMemberData object to the database, either overwriting an existing one with the same primary key or editing an existing instance.
     * 
     * @param accessUserAspectData the AccessUserAspectData to persist.
     */
    int createOrEdit(final RoleMemberData roleMember);
    
    
    /**
     * Removes an RoleMemberData from the database.
     * 
     * @param primaryKey
     *            The ID of the RoleMemberData to remove.         
     * @return true if removal was successful, false if no such role member was found
     */
    boolean remove(final int primaryKey);
    
}