package qa.qcri.aidr.task.dto.util;

import qa.qcri.aidr.task.dto.CrisisDTO;
import qa.qcri.aidr.task.entities.Crisis;

public class CrisisDTOHelper {

	public static Crisis toCrisis(CrisisDTO dto) {
		if (dto != null) {
			Crisis c = new Crisis();
			c.setCrisisID(dto.getCrisisID());
			c.setCrisisTypeID(dto.getCrisisTypeID());
			c.setName(dto.getName());
			c.setUserID(dto.getUserID());
			c.setCode(dto.getCode());

			return c;
		} 
		return null;
	}
	
	public static CrisisDTO toCrisisDTO(Crisis c) {
		if (c != null) {
			CrisisDTO dto = new CrisisDTO();
			dto.setCrisisID(c.getCrisisID());
			dto.setCrisisTypeID(c.getCrisisTypeID());
			dto.setName(c.getName());
			dto.setUserID(c.getUserID());
			dto.setCode(c.getCode());

			return dto;
		} 
		return null;
	}
}