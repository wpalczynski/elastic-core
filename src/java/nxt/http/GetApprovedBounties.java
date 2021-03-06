package nxt.http;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.NxtException;
import nxt.PowAndBounty;
import nxt.PowAndBountyAnnouncements;
import nxt.Work;
import nxt.db.DbIterator;



public final class GetApprovedBounties extends APIServlet.APIRequestHandler {

	static final GetApprovedBounties instance = new GetApprovedBounties();

	

	private GetApprovedBounties() {
		super(new APITag[] { APITag.ACCOUNTS, APITag.WC }, "account",
				"timestamp", "type", "subtype", "firstIndex", "lastIndex",
				"numberOfConfirmations", "withMessage");
	}

	@Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

	
		long wid = ParameterParser.getUnsignedLong(req, "work_id",true);
		
		byte hash[] = null;
		try {
			String readParam = ParameterParser.getAnnouncement(req, true);

			BigInteger b = new BigInteger(readParam, 16);
			hash = b.toByteArray();
		} catch (Exception e) {
			hash = null;
		}

		Work w = Work.getWork(wid);
		if(w == null || w.isClosed()){
			JSONObject response = new JSONObject();
			response.put("approved", "deprecated");
			return response;
		}
      
		 
		JSONObject response = new JSONObject();
		
		boolean hasIt = PowAndBountyAnnouncements.hasValidHash(wid, hash);
		boolean hasItFailed = PowAndBountyAnnouncements.hasHash(wid, hash);
		if(hasIt){
			response.put("approved", "true");
		}else if (hasItFailed){
			response.put("approved", "deprecated");
		}else{
			response.put("approved", "false");
		}
		
		
		return response;

	}

}
