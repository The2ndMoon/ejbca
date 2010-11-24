/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.ejb.hardtoken;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Complimentary class used to assign extended properties like copyof to a hard
 * token.
 * 
 * Id is represented by primary key of hard token table.
 */
@Entity
@Table(name = "HardTokenPropertyData")
@IdClass(HardTokenPropertyDataPK.class)
public class HardTokenPropertyData implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String PROPERTY_COPYOF = "copyof=";

    private String id;
    private String property;
    private String value;
	private int rowVersion = 0;
	private String rowProtection;

    /**
     * Entity holding data of a hard token properties.
     */
    public HardTokenPropertyData(String id, String property, String value) {
        setId(id);
        setProperty(property);
        setValue(value);
    }

    public HardTokenPropertyData() {
    }

    @Id
    @Column(name = "id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Id
    @Column(name = "property")
    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    @Column(name = "value")
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

	@Version
	@Column(name = "rowVersion", nullable = false, length = 5)
	public int getRowVersion() { return rowVersion; }
	public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

	@Column(name = "rowProtection", length = 10*1024)
	@Lob
	public String getRowProtection() { return rowProtection; }
	public void setRowProtection(String rowProtection) { this.rowProtection = rowProtection; }

    //
    // Search functions.
    //

    /** @return the found entity instance or null if the entity does not exist */
    public static HardTokenPropertyData findByPK(EntityManager entityManager, HardTokenPropertyDataPK pk) {
        return entityManager.find(HardTokenPropertyData.class, pk);
    }

    /**
     * @throws NonUniqueResultException
     *             if more than one entity with the name exists
     * @return the found entity instance or null if the entity does not exist
     */ 
    public static HardTokenPropertyData findByProperty(EntityManager entityManager, String id, String property) {
        HardTokenPropertyData ret = null;

        Query query = entityManager.createQuery("SELECT a FROM HardTokenPropertyData a WHERE a.id=:id AND a.property=:property");
        query.setParameter("id", id);
        query.setParameter("property", property);
        @SuppressWarnings("unchecked")
        List<HardTokenPropertyData> resultList = (List<HardTokenPropertyData>) query.getResultList();

        switch (resultList.size()) {
        case 0:
            ret = null;
            break;
        case 1:
            ret = resultList.get(0);
            break;
        default:
            throw new NonUniqueResultException("Several entries with the same primary key where found in the table HardTokenPropertyData.");
        }

        return ret;
    }

    /** @return return the query results as a List. */
    public static List<HardTokenPropertyData> findIdsByPropertyAndValue(EntityManager entityManager, String property, String value) {
        Query query = entityManager.createQuery("SELECT a FROM HardTokenPropertyData a WHERE a.property=:property AND a.value=:value");
        query.setParameter("property", property);
        query.setParameter("value", value);
        return query.getResultList();
    }
}
