/**
 * Implements operations for managing the document table of the aidr_predict DB
 * 
 * @author Koushik
 */
package qa.qcri.aidr.dbmanager.ejb.remote.facade.imp;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import qa.qcri.aidr.common.exception.PropertyNotSetException;
import qa.qcri.aidr.dbmanager.dto.CrisisDTO;
import qa.qcri.aidr.dbmanager.dto.DocumentDTO;
import qa.qcri.aidr.dbmanager.dto.NominalLabelDTO;
import qa.qcri.aidr.dbmanager.ejb.local.facade.impl.CoreDBServiceFacadeImp;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.CrisisResourceFacade;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.DocumentResourceFacade;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.NominalLabelResourceFacade;
import qa.qcri.aidr.dbmanager.entities.task.Document;

@Stateless(name="DocumentResourceFacadeImp")
public class DocumentResourceFacadeImp extends CoreDBServiceFacadeImp<Document, Long> implements DocumentResourceFacade  {

	private Logger logger = Logger.getLogger("db-manager-log");

	@EJB
	CrisisResourceFacade crisisEJB;

	@EJB
	NominalLabelResourceFacade nominalLabelEJB;

	public DocumentResourceFacadeImp() {
		super(Document.class);
	}

	@Override
	public void updateHasHumanLabel(DocumentDTO document) {
		Document doc = (Document) getByCriteria(Restrictions.eq("documentId", document.getDocumentID()));
		if (doc != null) {
			doc.setHasHumanLabels(true);
			update(doc);
		}
	}

	@Override
	public List<DocumentDTO> getDocumentCollectionForNominalLabel(Criterion criterion) throws PropertyNotSetException {
		String aliasTable = "documentNominalLabels";
		String aliasTableKey = "documentNominalLabels.id";
		String[] orderBy = {"documentId"};

		List<Document> fetchedList = getByCriteriaWithInnerJoinByOrder(criterion, "ASC", orderBy, null, aliasTable, Restrictions.isNotEmpty(aliasTableKey));
		if (fetchedList != null && !fetchedList.isEmpty()) {
			List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
			for (Document doc: fetchedList) {
				dtoList.add(new DocumentDTO(doc));
			}
			return dtoList;
		}
		return null;
	}


	@Override
	public int deleteNoLabelDocument(DocumentDTO document) {
		if (null == document) { 
			return 0;
		}

		logger.info("Received request for : " + document.getDocumentID());
		int deleteCount = 0;

		if (!document.getHasHumanLabels()) {
			try {
				//delete(document);
				String hql = "DELETE FROM document WHERE document.documentID = :documentID AND !document.hasHumanLabels";
				Session session = getCurrentSession();
				Query collectionDeleteQuery = session.createSQLQuery(hql);
				try {
					collectionDeleteQuery.setParameter("documentID", document.getDocumentID());
					deleteCount = collectionDeleteQuery.executeUpdate();
					logger.info("deleted count = " + deleteCount);
				} catch (Exception e) {
					logger.error("deletion query failed, document: " + document.getDocumentID());
					return 0;
				}
				logger.info("deletion success, deleted count = " + deleteCount);
				session.flush();
				return 1;

			} catch (Exception e) {
				logger.error("Deletion query failed");
				return 0;
			}
		}
		logger.info("Document has label. Not deleting.");
		return 0;
	}

	@Override
	public int deleteNoLabelDocument(List<DocumentDTO> collection) {
		int deleteCount = 0;
		if (collection != null && !collection.isEmpty()) {
			//Session session = getCurrentSession();
			try {
				//Transaction tx = session.beginTransaction();
				for (DocumentDTO d: collection) {
					deleteCount += deleteNoLabelDocument(d);
				}
				//tx.commit();
				logger.info("deleted count = " + deleteCount);
			} catch (Exception e) {
				logger.error("Collection deletion query failed");
			}
		}
		return deleteCount;
	}

	@Override
	public int deleteUnassignedDocument(DocumentDTO document) {
		String hql = "DELETE d FROM aidr_predict.document d LEFT JOIN aidr_predict.task_assignment t "
				+ "ON d.documentID = t.documentID WHERE (d.documentID = :documentID " 
				+ "AND t.documentID IS NULL AND !d.hasHumanLabels)";		
		if (!document.getHasHumanLabels()) {
			try {
				Query query = getCurrentSession().createSQLQuery(hql);
				query.setParameter("documentID", document.getDocumentID());
				int result = query.executeUpdate();
				return result;
			} catch (Exception e) {
				logger.error("Deletion query failed");
				return 0;
			}
		}
		return 0;
	}

	@Override
	public int deleteUnassignedDocument(Long documentID) {
		String hql = "DELETE d FROM aidr_predict.document d LEFT JOIN aidr_predict.task_assignment t "
				+ "ON d.documentID = t.documentID WHERE (d.documentID = :documentID " 
				+ "AND t.documentID IS NULL AND !d.hasHumanLabels)";
		try {
			Query query = getCurrentSession().createSQLQuery(hql);
			query.setParameter("documentID", documentID);
			int result = query.executeUpdate();
			return result;
		} catch (Exception e) {
			logger.error("Deletion query failed: " + e);
			return 0;
		}
	}


	@Override
	public int deleteUnassignedDocumentCollection(List<Long> documentIDList) {
		int deleteCount = 0;
		if (documentIDList != null && !documentIDList.isEmpty()) {
			logger.info("[deleteUnassignedDocumentCollection] Size of docList to delete: " + documentIDList.size());
			Session session = getCurrentSession();
			try {
				Transaction tx = session.beginTransaction();
				for (Long documentID: documentIDList) {
					deleteCount += deleteUnassignedDocument(documentID);
				}
				tx.commit();
			} catch (Exception e) {
				logger.error("[deleteUnassignedDocumentCollection] Collection deletion query failed");
				logger.error("Exception", e);
			}
		}
		return deleteCount;
	}

	/**
	 * Query very specific to deleting stale tasks only
	 */
	@Override
	public int deleteStaleDocuments(String joinType, String joinTable, String joinColumn,
			String sortOrder, String[] orderBy, 
			final String maxTaskAge, final String scanInterval) {

		logger.info("received request: " + joinType + ", " + joinTable + ", " 
				+ joinColumn + ", " + maxTaskAge + ", " + scanInterval);

		int deleteCount = 0;
		Session session = getCurrentSession();
		StringBuffer hql = new StringBuffer("DELETE d FROM aidr_predict.document d ");
		if (joinType.equalsIgnoreCase("LEFT JOIN") || joinType.equalsIgnoreCase("LEFT_JOIN")) {
			hql.append(" LEFT JOIN ").append(joinTable).append(" t ");
			hql.append(" ON d.").append(joinColumn).append(" = t.").append(joinColumn)
			.append(" WHERE ")
			.append("(!d.hasHumanLabels AND t.documentID IS NULL AND TIMESTAMPDIFF(")
			.append(getMetric(scanInterval))
			.append(", d.receivedAt, now()) > ");
		} else if (joinType.equalsIgnoreCase("JOIN")) {
			hql.append(" JOIN ").append(joinTable).append(" t ");
			hql.append(" ON d.").append(joinColumn).append(" = t.").append(joinColumn)
			.append(" WHERE ")
			.append("(!d.hasHumanLabels && TIMESTAMPDIFF(")
			.append(getMetric(scanInterval))
			.append(", t.assignedAt, now()) > ");
		}
		hql.append(" :task_expiry_age) ");

		if (orderBy != null) {
			hql.append(" ORDER BY ");
			for (int i = 0; i< orderBy.length - 1; i++) {
				hql.append(orderBy[i]).append(", ");
			}
			hql.append(orderBy[orderBy.length-1]).append(" ");
			if (sortOrder != null) {
				hql.append(sortOrder.toUpperCase()).append(" ; ");
			}
		}

		Query deleteQuery = session.createSQLQuery(hql.toString());
		deleteQuery.setParameter("task_expiry_age", Integer.parseInt(getTimeValue(maxTaskAge)));
		logger.info("Constructed query: " + deleteQuery.getQueryString());
		try {
			deleteCount = deleteQuery.executeUpdate();
			logger.info("[deleteStaleDocuments] number of deleted records = " + deleteCount);
		} catch (Exception e) {
			logger.error("Exception in executing SQL delete stale docs query");
		}
		return deleteCount;
	}

	/**
	 * 
	 * @param timeString
	 * @return duration in milliseconds. Negative indicates an invalid parse result
	 */

	@SuppressWarnings("unused")
	private long parseTime(final String timeString) {
		long duration = -1;		
		assert timeString != null;
		float value = Float.parseFloat(timeString.substring(0, timeString.length()-1));
		if (value > 0) {
			String suffix = timeString.substring(timeString.length() - 1, timeString.length());
			if (suffix != null) {
				if (suffix.equalsIgnoreCase("s"))
					duration = Math.round(value * 1000);
				else if (suffix.equalsIgnoreCase("m"))
					duration = Math.round(value * 60 * 1000) ;
				else if (suffix.equalsIgnoreCase("h"))
					duration = Math.round(value * 60 * 60 * 1000);
				else if (suffix.equalsIgnoreCase("d"))
					duration = Math.round(value * 60 * 60 * 24 * 1000);
				else
					duration = Math.round(value * 60 * 1000);		// default is minutes
			}
			else
				duration = Math.round(value * 60 * 1000);		// default is minutes
		}
		return duration;
	}


	private String getTimeValue(final String timeString) {
		assert timeString != null;
		return timeString.substring(0, timeString.length()-1);
	}


	private String getMetric(final String timeString) {
		assert timeString != null;
		String metric = "HOUR";			// default
		String suffix = timeString.substring(timeString.length() - 1, timeString.length());
		if (suffix != null) {
			if (suffix.equalsIgnoreCase("s"))
				metric = "SECOND"; 
			else if (suffix.equalsIgnoreCase("m"))
				metric = "MINUTE";
			else if (suffix.equalsIgnoreCase("h"))
				metric = "HOUR";
			else if (suffix.equalsIgnoreCase("d"))
				metric = "DAY";
		}
		return metric;
	}

	@Override
	public DocumentDTO addDocument(DocumentDTO doc) {
		try {
			Document d = doc.toEntity();
			em.persist(d);
			em.flush();
			em.refresh(d);
			return new DocumentDTO(d);
		} catch (Exception e) {
			logger.error("Error in addDocument.", e);
			return null;
		}
	}

	@Override
	public DocumentDTO editDocument(DocumentDTO doc) throws PropertyNotSetException {
		try {
			Document d = doc.toEntity();
			Document oldDoc = getById(d.getDocumentId()); 
			if (oldDoc != null) {
				oldDoc = em.merge(d);
				return oldDoc != null ? new DocumentDTO(oldDoc) : null;
			} else {
				throw new RuntimeException("Not found");
			}
		} catch (Exception e) {
			logger.error("Exception in merging/updating document: " + doc.getDocumentID(), e);
		}
		return null;
	}

	@Override
	public Integer deleteDocument(DocumentDTO doc) {
		try {
			Document managed = em.merge(doc.toEntity());
			em.remove(managed);
		} catch (Exception e) {
			logger.warn("Warning! Couldn't delete document with ID : " + doc.getDocumentID());
			return 0;
		}
		return 1;
	}

	@Override
	public List<DocumentDTO> findByCriteria(String columnName, Object value) throws PropertyNotSetException {
		List<Document> list = getAllByCriteria(Restrictions.eq(columnName,value));
		List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
		if (list != null && !list.isEmpty()) {
			for (Document c: list) {
				dtoList.add(new DocumentDTO(c));
			}
		}
		return dtoList;
	}

	@Override
	public DocumentDTO findDocumentByID(Long id) throws PropertyNotSetException {
		Document doc = getById(id);
		if (doc != null) {
			DocumentDTO dto = new DocumentDTO(doc);
			return dto;
		} else {
			return null;
		}
	}

	@Override
	public List<DocumentDTO> findDocumentsByCrisisID(Long crisisId) throws PropertyNotSetException {
		List<DocumentDTO> dtoList = findByCriteria("crisis.crisisId", crisisId);
		return dtoList;
	}

	@Override
	public DocumentDTO getDocumentWithAllFieldsByID(Long id) throws PropertyNotSetException {
		Document doc = getById(id);
		if (doc != null) {
			Hibernate.initialize(doc.getCrisis());
			Hibernate.initialize(doc.getDocumentNominalLabels());
			Hibernate.initialize(doc.getTaskAssignments());

			DocumentDTO dto = new DocumentDTO(doc);
			return dto;
		} else {
			return null;
		}
	}

	@Override
	public boolean isDocumentExists(Long id) throws PropertyNotSetException {
		DocumentDTO dto = findDocumentByID(id); 
		return dto != null ? true : false;
	}

	@Override
	public List<DocumentDTO> getAllDocuments() throws PropertyNotSetException {
		List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
		List<Document> list = getAll();
		if (list != null && !list.isEmpty()) {
			for (Document doc : list) {
				DocumentDTO dto = new DocumentDTO(doc);
				dtoList.add(dto);
			}
		}
		logger.error("Done creating DTO list, size = " + dtoList.size());
		return dtoList;
	}

	@Override
	public List<DocumentDTO> findLabeledDocumentsByCrisisID(Long crisisId) throws PropertyNotSetException {
		Criterion criterion = Restrictions.conjunction()
				.add(Restrictions.eq("crisis.crisisId",crisisId))
				.add(Restrictions.eq("hasHumanLabels", true));
		List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
		List<Document> list = this.getAllByCriteria(criterion);
		if (list != null && !list.isEmpty()) {
			for (Document doc : list) {
				DocumentDTO dto = new DocumentDTO(doc);
				dtoList.add(dto);
			}
		}
		logger.info("Done creating DTO list, size = " + dtoList.size());
		return dtoList;
	}

	@Override
	public List<DocumentDTO> findUnLabeledDocumentsByCrisisID(Long crisisId) throws PropertyNotSetException {
		Criterion criterion = Restrictions.conjunction()
				.add(Restrictions.eq("crisis.crisisId",crisisId))
				.add(Restrictions.eq("hasHumanLabels", false));
		List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
		List<Document> list = this.getAllByCriteria(criterion);
		if (list != null && !list.isEmpty()) {
			for (Document doc : list) {
				DocumentDTO dto = new DocumentDTO(doc);
				dtoList.add(dto);
			}
		}
		logger.info("Done creating DTO list, size = " + dtoList.size());
		return dtoList;
	}

	@Override
	public Integer getDocumentCountForNominalLabelAndCrisis(Long nominalLabelID, String crisisCode) {
		if (nominalLabelID != null) {
			String aliasTable = "documentNominalLabels";
			String aliasTableKeyField = "documentNominalLabels.id.nominalLabelId";
			String[] orderBy = {"documentId"};
			Criteria criteria = null;
			
			try {
				CrisisDTO cdto = crisisEJB.getCrisisByCode(crisisCode); 
				
				Criterion criterion = Restrictions.conjunction()
						.add(Restrictions.eq("crisis.crisisId",cdto.getCrisisID()))
						.add(Restrictions.eq("hasHumanLabels", true));
				
				Criterion aliasCriterion =  Restrictions.eq(aliasTableKeyField, nominalLabelID);
				
				// get just the documentIDs
				Projection projection = Projections.property("documentId");
				
				//List<Document> docList = this.getByCriteriaWithInnerJoinByOrder(criterion, "DESC", orderBy, null, aliasTable, aliasCriterion);
				criteria = createCriteria(criterion, "DESC", orderBy, null, aliasTable, aliasCriterion, new Projection[] {projection});
				List<Long> docIDList = criteria.list();
				
				if (docIDList != null && !docIDList.isEmpty()) {
					return docIDList.size();
				}
			} catch (Exception e) {
				logger.error("getDocumentCountForNominalLabelAndCrisis failed, criteria = " + criteria.toString(), e);
				return 0;
			}
		}
		return 0;
	}
	
	@Override
	public List<DocumentDTO> getDocumentCollectionWithNominalLabelData(Long nominalLabelID) throws Exception {
		List<DocumentDTO> dtoList = new ArrayList<DocumentDTO>();
		if (nominalLabelID != null) {
			String aliasTable = "documentNominalLabels";
			String aliasTableKeyField = "documentNominalLabels.id.nominalLabelId";
			String[] orderBy = {"documentId"};

			Criterion criterion = Restrictions.eq("hasHumanLabels", true);
			
			Criterion aliasCriterion =  Restrictions.eq(aliasTableKeyField, nominalLabelID);
			List<Document> docList = this.getByCriteriaWithInnerJoinByOrder(criterion, "DESC", orderBy, null, aliasTable, aliasCriterion);
			if (docList != null && !docList.isEmpty()) {
				logger.info("[getDocumentCollectionWithNominalLabelData] Fetched size = " + docList.size());
				NominalLabelDTO nominalLabel = nominalLabelEJB.getNominalLabelByID(nominalLabelID);
				for (Document doc: docList) {
					DocumentDTO dto = new DocumentDTO(doc);
					dto.setNominalLabelDTO(nominalLabel);
					dtoList.add(dto);	
				}
				logger.info("[getDocumentCollectionWithNominalLabelData] Done creating DTO list, size = " + dtoList.size());
			}
		}
		return dtoList;
	}
	
	

	@Override
	public Integer getUnlabeledDocumentsCountByCrisisID(Long crisisId) throws PropertyNotSetException {
		Criterion criterion = Restrictions.conjunction()
				.add(Restrictions.eq("crisis.crisisId",crisisId))
				.add(Restrictions.eq("hasHumanLabels", false));
		List<Document> documentList = this.getAllByCriteria(criterion);
		return documentList != null ? Integer.valueOf(documentList.size()) : 0;
	}
	
	@Override
	public boolean deleteDocuments(List<DocumentDTO> documents){
		try {
			for (DocumentDTO documentDTO : documents) {
				deleteDocument(documentDTO);
			}
			return true;
		} catch (Exception e) {
			logger.error("Error in deleting document.");
			return false;
		}
		
		
	}

	@Override
	public List<Long> getUnassignedDocumentIDsByCrisisID(Long crisisID, Integer count) {
		
		List<Long> docIDList = new ArrayList<Long>();
		Criteria criteria = null;
		try {
			String aliasTable = "taskAssignments";
			String order = "ASC";
			String aliasTableKey = "taskAssignments.id.documentId";
			String[] orderBy = {"valueAsTrainingSample", "documentId"};
			Criterion criterion = Restrictions.conjunction()
					.add(Restrictions.eq("crisis.crisisId",crisisID))
					.add(Restrictions.eq("hasHumanLabels",false));

			// get just the documentIDs
			Projection projection = Projections.property("documentId");
			Criterion aliasCriterion =  (Restrictions.isNull(aliasTableKey));
			criteria = createCriteria(criterion, order, orderBy, count, aliasTable, aliasCriterion, new Projection[] {projection});
			docIDList = criteria.list();
			return docIDList;
				
		} catch (Exception e) {
			logger.error("getByCriteriaWithAliasByOrder failed, criteria = " + criteria.toString(), e);
			throw new HibernateException("getByCriteriaWithAliasByOrder failed, criteria = " + criteria.toString());
		}
	}

}