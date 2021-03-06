/******************************************************************************
 * Copyright Â© 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * Represents a single shuffling participant
 */
package nxt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;

public final class PowAndBounty {


    public enum Event {
        POW_SUBMITTED, BOUNTY_SUBMITTED
    }

    

    private static final Listeners<PowAndBounty, Event> listeners = new Listeners<>();

    private static final DbKey.LongKeyFactory<PowAndBounty> powAndBountyDbKeyFactory = new DbKey.LongKeyFactory<PowAndBounty>("id") {

        @Override
        public DbKey newKey(PowAndBounty participant) {
            return participant.dbKey;
        }

    };

    private static final VersionedEntityDbTable<PowAndBounty> powAndBountyTable = new VersionedEntityDbTable<PowAndBounty>("pow_and_bounty", powAndBountyDbKeyFactory) {

        @Override
        protected PowAndBounty load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PowAndBounty(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PowAndBounty participant) throws SQLException {
            participant.save(con);
        }

    };

    public static PowAndBounty getPowOrBountyById(long id) {
        return powAndBountyTable.get(powAndBountyDbKeyFactory.newKey(id));
    }

    public static boolean addListener(Listener<PowAndBounty> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<PowAndBounty> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }
   
    public static DbIterator<PowAndBounty> getPows(long wid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)).and(
                        new DbClause.BooleanClause("latest", true)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getBounties(long wid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)).and(
                        new DbClause.BooleanClause("latest", true)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getPows(long wid, long aid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)).and(
                        new DbClause.LongClause("account_id", aid)).and(
                                new DbClause.BooleanClause("latest", true)), 0, -1, "");
    }
    public static DbIterator<PowAndBounty> getBounties(long wid, long aid) {
        return powAndBountyTable.getManyBy(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)).and(
                        new DbClause.LongClause("account_id", aid)).and(
                                new DbClause.BooleanClause("latest", true)), 0, -1, "");
    }
    
    static Map<Long, Integer> GetAccountBountyMap(long wid){
    	DbIterator<PowAndBounty> it = getBounties(wid);
    	Map<Long, Integer> map = new HashMap<Long, Integer>();
    	while(it.hasNext()){
    		PowAndBounty p = it.next();
    		if(map.containsKey(p.getAccountId())==false){
    			map.put(p.getAccountId(), 1);
    		}else{
    			int ik = map.get(p.getAccountId());
    			map.put(p.getAccountId(), ik+1);
    		}
    	}
    	it.close();
    	return map;
    }

    static void addPow(Transaction transaction, Attachment.PiggybackedProofOfWork attachment) {
    	PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
    	powAndBountyTable.insert(shuffling);
    	
    	
        listeners.notify(shuffling, Event.POW_SUBMITTED);
    }
    static void addBounty(Transaction transaction, Attachment.PiggybackedProofOfBounty attachment) {
    	PowAndBounty shuffling = new PowAndBounty(transaction, attachment);
    	powAndBountyTable.insert(shuffling);
        listeners.notify(shuffling, Event.BOUNTY_SUBMITTED);
    }
    static int getPowCount(long wid) {
        return powAndBountyTable.getCount(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", true)));
    }
    
    static boolean hasHash(long workId, byte[] hash) {
        return powAndBountyTable.getCount(new DbClause.BytesClause("hash", hash).and(new DbClause.LongClause("work_id", workId)))>0;
    }
    
    static int getBountyCount(long wid) {
        return powAndBountyTable.getCount(new DbClause.LongClause("work_id", wid).and(
                new DbClause.BooleanClause("is_pow", false)));
    }
    static void init() {}
    private final long id;
    private final boolean is_pow;
    private boolean too_late;
    private final long work_id;
    private final long accountId;
    private final DbKey dbKey;
    private final byte[] multiplicator;
    private final byte[] hash;
    
    public void applyPowPayment(Block bl){
    	Work w = Work.getWorkByWorkId(this.work_id);
    	
    	if(w.isClosed() == false && w.isClose_pending() == false){
	    	// Now create ledger event for "bounty submission"
	        AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_POW;
	        Account participantAccount = Account.getAccount(this.accountId);
	        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, w.getXel_per_pow());
	        // Reduce work remaining xel
	        w.reduce_one_pow_submission(bl);
    	}else{
    		this.too_late = true;
    		this.powAndBountyTable.insert(this);
    	}
    	
        
    }
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public void applyBounty(Block bl){
    	Work w = Work.getWorkByWorkId(this.work_id);
    	if(w.isClosed() == false){
    		// Immediate payout incl. the bounty deposit
    		AccountLedger.LedgerEvent event = AccountLedger.LedgerEvent.WORK_BOUNTY_PAYOUT;
	        Account participantAccount = Account.getAccount(this.accountId);
	        participantAccount.addToBalanceAndUnconfirmedBalanceNQT(event, this.id, w.getXel_per_bounty() + Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION);
	        // Reduce bounty fund entirely
	        w.kill_bounty_fund(bl);
    	}else{
    		this.too_late = true;
    		this.powAndBountyTable.insert(this);
    	}
    }
    
    private PowAndBounty(Transaction transaction, Attachment.PiggybackedProofOfWork attachment) {
    	this.id = transaction.getId();
        this.work_id = attachment.getWorkId();
        this.accountId = transaction.getSenderId();
        this.dbKey = powAndBountyDbKeyFactory.newKey(id);
        this.multiplicator = attachment.getMultiplicator();
        this.is_pow = true;
        this.hash = attachment.getHash(); // FIXME TODO
        this.too_late = false;
    }
    private PowAndBounty(Transaction transaction, Attachment.PiggybackedProofOfBounty attachment) {
    	this.id = transaction.getId();
        this.work_id = attachment.getWorkId();
        this.accountId = transaction.getSenderId();
        this.dbKey = powAndBountyDbKeyFactory.newKey(id);
        this.multiplicator = attachment.getMultiplicator();
        this.is_pow = false;
        this.hash = attachment.getHash(); // FIXME TODO
        this.too_late = false;
    }
    private PowAndBounty(ResultSet rs, DbKey dbKey) throws SQLException {
    	this.id = rs.getLong("id");
        this.work_id = rs.getLong("work_id");
        this.accountId = rs.getLong("account_id");
        this.is_pow = rs.getBoolean("is_pow");
        this.dbKey = dbKey;
        this.multiplicator = rs.getBytes("multiplicator");
        this.too_late = rs.getBoolean("too_late");
        this.hash = rs.getBytes("hash");
    }

    public byte[] getMultiplicator() {
		return multiplicator;
	}

	private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO pow_and_bounty (id, too_late, work_id, hash, account_id, multiplicator, is_pow,"
                + " height) " + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setBoolean(++i, this.too_late);
            pstmt.setLong(++i, this.work_id);
            DbUtils.setBytes(pstmt, ++i, this.hash);
            pstmt.setLong(++i, this.accountId);
            pstmt.setBytes(++i, multiplicator);
            pstmt.setBoolean(++i, this.is_pow);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getAccountId() {
        return accountId;
    }

	public JSONObject toJsonObject() {
		JSONObject response = new JSONObject();
		response.put("id",Convert.toUnsignedLong(this.id));
		Transaction t = TransactionDb.findTransaction(this.id);
		response.put("date",Convert.toUnsignedLong(t.getTimestamp()));
		response.put("multiplicator",Arrays.toString(this.multiplicator));
		return response;
	}

}