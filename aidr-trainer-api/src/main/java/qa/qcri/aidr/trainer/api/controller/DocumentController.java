package qa.qcri.aidr.trainer.api.controller;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import qa.qcri.aidr.dbmanager.dto.DocumentDTO;
import qa.qcri.aidr.trainer.api.service.DocumentService;
import qa.qcri.aidr.trainer.api.template.TaskBufferJsonModel;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jlucas
 * Date: 3/4/14
 * Time: 9:17 PM
 * To change this template use File | Settings | File Templates.
 */
@Path("/document")
@Component
public class DocumentController {
    protected static Logger logger = Logger.getLogger("DocumentController");

    @Autowired
    private DocumentService documentService;

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/getbatchtaskbuffer/{userName}/{crisisID}/{maxresult}")
    public List<DocumentDTO> getAllTaskBufferToAssign(
            @PathParam("userName") String userName,
            @PathParam("crisisID") String crisisID,
            @PathParam("maxresult") String maxresult){

    	logger.info("Request for batch task fetch, crisisID = " + crisisID + ", user = " + userName + ", count = " + maxresult);
        return documentService.getDocumentForTask(new Long(crisisID), Integer.valueOf(maxresult), userName);

    }

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/getassignabletask/{userName}/{crisisID}/{maxresult}")
    public List<TaskBufferJsonModel> getOneTaskBufferToAssign(@PathParam("crisisID") String crisisID,
                                                              @PathParam("userName") String userName,
                                                              @PathParam("maxresult") String maxresult){

        DocumentDTO document = null;
        Long id = new Long(crisisID);
        logger.info("Going to fetch internal training document for crisisID = " + crisisID + ", userName = " + userName + ", count = " + maxresult);
        if(userName != null){
            List<DocumentDTO> documents =  documentService.getDocumentForOneTask(id,Integer.valueOf(maxresult),userName );
            if(documents!= null){
                if(documents.size() > 0){
                    document = documents.get(0);
                    logger.info("Fetched document for internal tagging task: " + document.getDocumentID() + ", for crisisID = " + document.getCrisisDTO().getCrisisID());
                }
            }

        }
        List<TaskBufferJsonModel> jsonData = documentService.findOneDocumentForTaskByCririsID(document, id);
        for (int i = 0;i < jsonData.size();i++) {
        	logger.info("To be returned json Data, documentID = " + jsonData.get(i).getDocumentID() + ", for crisisID = " + jsonData.get(i).getCrisisID() );
        }
        return jsonData;
    }
}
