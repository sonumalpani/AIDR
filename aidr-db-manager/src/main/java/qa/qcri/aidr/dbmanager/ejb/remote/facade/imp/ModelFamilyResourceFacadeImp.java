/**
 * Implements operations for managing the model_family table of the aidr_predict DB
 * 
 * @author Koushik
 */
package qa.qcri.aidr.dbmanager.ejb.remote.facade.imp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;

import qa.qcri.aidr.common.exception.PropertyNotSetException;
import qa.qcri.aidr.dbmanager.dto.CrisisDTO;
import qa.qcri.aidr.dbmanager.dto.ModelFamilyDTO;
import qa.qcri.aidr.dbmanager.dto.NominalAttributeDTO;
import qa.qcri.aidr.dbmanager.dto.taggerapi.TaggersForCodes;
import qa.qcri.aidr.dbmanager.ejb.local.facade.impl.CoreDBServiceFacadeImp;
import qa.qcri.aidr.dbmanager.ejb.remote.facade.ModelFamilyResourceFacade;
import qa.qcri.aidr.dbmanager.entities.misc.Crisis;
import qa.qcri.aidr.dbmanager.entities.model.ModelFamily;
import qa.qcri.aidr.dbmanager.entities.model.NominalAttribute;


@Stateless(name="ModelFamilyResourceFacadeImp")
public class ModelFamilyResourceFacadeImp extends CoreDBServiceFacadeImp<ModelFamily, Long> implements ModelFamilyResourceFacade {

	private static Logger logger = Logger.getLogger("db-manager-log");

	public ModelFamilyResourceFacadeImp() {
		super(ModelFamily.class);
	}

	@Override
	public List<ModelFamilyDTO> getAllModelFamilies() throws PropertyNotSetException {
		List<ModelFamilyDTO> modelFamilyDTOList = new ArrayList<ModelFamilyDTO>();
		List<ModelFamily> modelFamilyList = getAll();
		if (modelFamilyList != null && !modelFamilyList.isEmpty()) {
			for (ModelFamily modelFamily : modelFamilyList) {
				//Hibernate.initialize(modelFamily.getModels()); Enlable this line if models need to be populated.
				modelFamilyDTOList.add(new ModelFamilyDTO(modelFamily));
			}
		}
		return modelFamilyDTOList; //returns empty list if no data is found in the database
	}

	@Override
	public List<ModelFamilyDTO> getAllModelFamiliesByCrisis(Long crisisID) throws PropertyNotSetException {
		List<ModelFamilyDTO> modelFamilyDTOList = new ArrayList<ModelFamilyDTO>();
		Crisis crisis = getEntityManager().find(Crisis.class, crisisID);
		Hibernate.initialize(crisis.getModelFamilies());
		List<ModelFamily> modelFamilyList = crisis.getModelFamilies();
		if (modelFamilyList != null && !modelFamilyList.isEmpty()) {
			for (ModelFamily modelFamily : modelFamilyList) {
				Hibernate.initialize(modelFamily.getModels());
				Hibernate.initialize(modelFamily.getNominalAttribute());
				Hibernate.initialize(modelFamily.getCrisis());
				modelFamilyDTOList.add(new ModelFamilyDTO(modelFamily));
			}
		}
		return modelFamilyDTOList; //returns empty list if no data is found in the database
	}

	@Override
	public ModelFamilyDTO getModelFamilyByID(Long id) throws PropertyNotSetException {
		ModelFamily mf = this.getById(id);
		Hibernate.initialize(mf.getModels());
		Hibernate.initialize(mf.getNominalAttribute());
		return mf != null ? new ModelFamilyDTO(mf) : null;
	}

	@Override
	public boolean addCrisisAttribute(ModelFamilyDTO modelFamily) throws PropertyNotSetException {
		try {
			Crisis crisis = getEntityManager().find(Crisis.class, modelFamily.getCrisisDTO().getCrisisID());
			NominalAttribute attribute = getEntityManager().find(NominalAttribute.class, modelFamily.getNominalAttributeDTO().getNominalAttributeId());
			modelFamily.setCrisisDTO(new CrisisDTO(crisis));
			modelFamily.setNominalAttributeDTO(new NominalAttributeDTO(attribute));
			getEntityManager().persist(modelFamily.toEntity());
		} catch (HibernateException he) {
			logger.error("Hibernate exception on adding ModelFamily.\n" + he.getStackTrace());
			return false;
		}
		return true;
	}

	@Override
	public boolean deleteModelFamily(Long modelFamilyID) throws PropertyNotSetException {
		ModelFamily modelFamily = getEntityManager().find(ModelFamily.class, modelFamilyID);
		if (modelFamily != null) {
			try {
				getEntityManager().remove(modelFamily);
			} catch (HibernateException he) {
				logger.error("Hibernate exception on deleting ModelFamily using ID=" + modelFamilyID + he.getStackTrace());
				return false; // hibernate delete operation failed. Details in the logs.
			}
			return true; // successfully deleted.
		} else {
			return false; // delete operation failed becuase no modelfamily is found against given ID.
		}
	}

	@Override
	public List<TaggersForCodes> getTaggersByCodes(final List<String> codes) {
		List<qa.qcri.aidr.dbmanager.dto.taggerapi.TaggersForCodes> result = new ArrayList<TaggersForCodes>();

		String sql = "select c.code as code, " +
				" count(mf.modelFamilyID) as modelsCount " +
				" from model_family mf " +
				" right outer join crisis c on c.crisisID = mf.crisisID " +
				" where c.code in :codes " +
				" group by mf.crisisID ";

		Query query = em.createNativeQuery(sql);
		query.setParameter("codes", codes);
		List<Object[]> rows = null;
		try {
			rows = query.getResultList();
		} catch (NoResultException e) {
			logger.warn("No result for codes : " + codes);
			return null;
		}
		for (Object[] row : rows) {
			TaggersForCodes taggersForCodes = new TaggersForCodes();
			taggersForCodes.setCode((String) row[0]);
			taggersForCodes.setCount((BigInteger) row[1]);
			result.add(taggersForCodes);
		}

		return result;
	}
}
