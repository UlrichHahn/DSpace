/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl;

import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.ResourcePolicy_;
import org.dspace.content.Collection;
import org.dspace.content.Collection_;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.dao.CollectionDAO;
import org.dspace.core.AbstractHibernateDSODAO;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * Hibernate implementation of the Database Access Object interface class for the Collection object.
 * This class is responsible for all database calls for the Collection object and is autowired by spring
 * This class should never be accessed directly.
 *
 * @author kevinvandevelde at atmire.com
 */
public class CollectionDAOImpl extends AbstractHibernateDSODAO<Collection> implements CollectionDAO {
    protected CollectionDAOImpl() {
        super();
    }

    /**
     * Get all collections in the system. These are alphabetically sorted by
     * collection name.
     *
     * @param context
     *            DSpace context object
     * @param order order by MetadataField
     * @return the collections in the system
     * @throws SQLException if database error
     */
    @Override
    public List<Collection> findAll(Context context, MetadataField order) throws SQLException {
        return findAll(context, order, null, null);
    }

    @Override
    public List<Collection> findAll(Context context, MetadataField order, Integer limit, Integer offset)
        throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ").append(Collection.class.getSimpleName()).append(" FROM Collection as ")
             .append(Collection.class.getSimpleName()).append(" ");
        addMetadataLeftJoin(query, Collection.class.getSimpleName(), Arrays.asList(order));
        addMetadataSortQuery(query, Arrays.asList(order), null);

        Query hibernateQuery = createQuery(context, query.toString());
        if (offset != null) {
            hibernateQuery.setFirstResult(offset);
        }
        if (limit != null) {
            hibernateQuery.setMaxResults(limit);
        }
        hibernateQuery.setParameter(order.toString(), order.getID());
        return list(hibernateQuery);
    }

    @Override
    public Collection findByTemplateItem(Context context, Item item) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Collection.class);
        Root<Collection> collectionRoot = criteriaQuery.from(Collection.class);
        //TODO Used to be template_item, may be wrong
        criteriaQuery.select(collectionRoot);
        criteriaQuery.where(criteriaBuilder.equal(collectionRoot.get(Collection_.template), item));
        return uniqueResult(context, criteriaQuery, false, Collection.class, -1, -1);
    }

    @Override
    public Collection findByGroup(Context context, Group group) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Collection.class);
        Root<Collection> collectionRoot = criteriaQuery.from(Collection.class);
        criteriaQuery.select(collectionRoot);
        criteriaQuery
            .where(criteriaBuilder.or(criteriaBuilder.equal(collectionRoot.get(Collection_.workflowStep1), group),
                                      criteriaBuilder.equal(collectionRoot.get(Collection_.workflowStep2), group),
                                      criteriaBuilder.equal(collectionRoot.get(Collection_.workflowStep3), group),
                                      criteriaBuilder.equal(collectionRoot.get(Collection_.submitters), group),
                                      criteriaBuilder.equal(collectionRoot.get(Collection_.admins), group)
                   )
        );
        return singleResult(context, criteriaQuery);
    }

    @Override
    public List<Collection> findAuthorized(Context context, EPerson ePerson, List<Integer> actions)
        throws SQLException {
        //        TableRowIterator tri = DatabaseManager.query(context,
//                "SELECT * FROM collection, resourcepolicy, eperson " +
//                        "WHERE resourcepolicy.resource_id = collection.collection_id AND " +
//                        "eperson.eperson_id = resourcepolicy.eperson_id AND "+
//                        "resourcepolicy.resource_type_id = 3 AND "+
//                        "( resourcepolicy.action_id = 3 OR resourcepolicy.action_id = 11 ) AND "+
//                        "eperson.eperson_id = ?", context.getCurrentUser().getID());

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, Collection.class);
        Root<Collection> collectionRoot = criteriaQuery.from(Collection.class);
        Join<Collection, ResourcePolicy> join = collectionRoot.join("resourcePolicies");
        List<Predicate> orPredicates = new LinkedList<Predicate>();
        for (Integer action : actions) {
            orPredicates.add(criteriaBuilder.equal(join.get(ResourcePolicy_.actionId), action));
        }
        Predicate orPredicate = criteriaBuilder.or(orPredicates.toArray(new Predicate[] {}));
        criteriaQuery.select(collectionRoot);
        criteriaQuery.where(
            criteriaBuilder.and(criteriaBuilder.equal(join.get(ResourcePolicy_.resourceTypeId), Constants.COLLECTION),
                                criteriaBuilder.equal(join.get(ResourcePolicy_.eperson), ePerson),
                                orPredicate));
        return list(context, criteriaQuery, true, Collection.class, -1, -1);
    }

    @Override
    public List<Collection> findAuthorizedByGroup(Context context, EPerson ePerson, List<Integer> actions)
        throws SQLException {
        //        TableRowIterator tri = DatabaseManager.query(context,
        //                "SELECT \n" +
        //                        "  * \n" +
        //                        "FROM \n" +
        //                        "  public.eperson, \n" +
        //                        "  public.epersongroup2eperson, \n" +
        //                        "  public.epersongroup, \n" +
        //                        "  public.group2group, \n" +
        //                        "  public.resourcepolicy rp_parent, \n" +
        //                        "  public.collection\n" +
        //                        "WHERE \n" +
        //                        "  epersongroup2eperson.eperson_id = eperson.eperson_id AND\n" +
        //                        "  epersongroup.eperson_group_id = epersongroup2eperson.eperson_group_id AND\n" +
        //                        "  group2group.child_id = epersongroup.eperson_group_id AND\n" +
        //                        "  rp_parent.epersongroup_id = group2group.parent_id AND\n" +
        //                        "  collection.collection_id = rp_parent.resource_id AND\n" +
        //                        "  eperson.eperson_id = ? AND \n" +
        //                        "  (rp_parent.action_id = 3 OR \n" +
        //                        "  rp_parent.action_id = 11  \n" +
        //                        "  )  AND rp_parent.resource_type_id = 3;", context.getCurrentUser().getID());
        StringBuilder query = new StringBuilder();
        query.append("select c from Collection c join c.resourcePolicies rp join rp.epersonGroup rpGroup WHERE ");
        for (int i = 0; i < actions.size(); i++) {
            Integer action = actions.get(i);
            if (i != 0) {
                query.append(" AND ");
            }
            query.append("rp.actionId=").append(action);
        }
        query.append(" AND rp.resourceTypeId=").append(Constants.COLLECTION);
        query.append(
            " AND rp.epersonGroup.id IN (select g.id from Group g where (from EPerson e where e.id = :eperson_id) in " +
                "elements(epeople))");
        Query persistenceQuery = createQuery(context, query.toString());
        persistenceQuery.setParameter("eperson_id", ePerson.getID());
        persistenceQuery.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return list(persistenceQuery);


    }

    @Override
    public List<Collection> findCollectionsWithSubscribers(Context context) throws SQLException {
        return list(createQuery(context, "SELECT DISTINCT col FROM Subscription s join  s.collection col"));
    }

    @Override
    public int countRows(Context context) throws SQLException {
        return count(createQuery(context, "SELECT count(*) FROM Collection"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map.Entry<Collection, Long>> getCollectionsWithBitstreamSizesTotal(Context context)
        throws SQLException {
        String q = "select col as collection, sum(bit.sizeBytes) as totalBytes from Item i join i.collections col " +
            "join i.bundles bun join bun.bitstreams bit group by col";
        Query query = createQuery(context, q);

        List<Object[]> list = query.getResultList();
        List<Map.Entry<Collection, Long>> returnList = new LinkedList<>();
        for (Object[] o : list) {
            returnList.add(new AbstractMap.SimpleEntry<>((Collection) o[0], (Long) o[1]));
        }
        return returnList;
    }
}