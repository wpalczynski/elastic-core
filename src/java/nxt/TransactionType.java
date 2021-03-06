/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
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

package nxt;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.json.simple.JSONObject;

import elastic.pl.interpreter.ASTCompilationUnit.POW_CHECK_RESULT;
import nxt.AccountLedger.LedgerEvent;
import nxt.util.Convert;


public abstract class TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte TYPE_MESSAGING = 1;
    private static final byte TYPE_ACCOUNT_CONTROL = 2;
    private static final byte TYPE_WORK_CONTROL = 3;
    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
    private static final byte SUBTYPE_PAYMENT_REDEEM = 1;
    private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    private static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 1;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 2;
    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    private static final byte SUBTYPE_WORK_CONTROL_NEW_TASK = 0;
	private static final byte SUBTYPE_WORK_CONTROL_CANCEL_TASK = 1;
	private static final byte SUBTYPE_WORK_CONTROL_PROOF_OF_WORK = 2;
	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY = 3;
	private static final byte SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT = 4;
	private static final byte SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST = 5;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    case SUBTYPE_PAYMENT_REDEEM:
                        return Payment.REDEEM;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        return Messaging.HUB_ANNOUNCEMENT;
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                        
                    default:
                        return null;
                }
           
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
                    default:
                        return null;
                }
            case TYPE_WORK_CONTROL:
    			switch (subtype) {
    			case SUBTYPE_WORK_CONTROL_NEW_TASK:
    				return TransactionType.WorkControl.NEW_TASK;
    			case SUBTYPE_WORK_CONTROL_PROOF_OF_WORK:
    				return TransactionType.WorkControl.PROOF_OF_WORK;
    			case SUBTYPE_WORK_CONTROL_BOUNTY:
    				return TransactionType.WorkControl.BOUNTY;
    			case SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT:
    				return TransactionType.WorkControl.BOUNTY_ANNOUNCEMENT;
    			case SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST:
    				return TransactionType.WorkControl.CANCEL_TASK_REQUEST;
    			default:
    				return null;
    			}
            default:
                return null;
        }
    }


    public boolean zeroFeeTransaction() {
		return false;
	}


	public boolean moneyComesFromNowhere() {
		return false;
	}


	public boolean specialDepositTX() {
		return false;
	}


	TransactionType() {}

    public abstract byte getType();

    public abstract byte getSubtype();

    public abstract LedgerEvent getLedgerEvent();

    abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException;

    abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException;

    abstract void validateAttachment(Transaction transaction) throws NxtException.ValidationException;

    // return false iff double spending
    final boolean applyUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        long amountNQT = transaction.getAmountNQT();
        long feeNQT = transaction.getFeeNQT();
        if (transaction.referencedTransactionFullHash() != null) {
            feeNQT = Math.addExact(feeNQT, Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        if (senderAccount.getUnconfirmedBalanceNQT() < totalAmountNQT
                && !(transaction.getTimestamp() == 0 && Arrays.equals(transaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY))) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), -amountNQT, -feeNQT);
        if (!applyAttachmentUnconfirmed(transaction, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), amountNQT, feeNQT);
            return false;
        }
        return true;
    }

    abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    final void apply(TransactionImpl transaction, Account senderAccount, Account recipientAccount) {
        long amount = transaction.getAmountNQT();
        long transactionId = transaction.getId();
        senderAccount.addToBalanceNQT(getLedgerEvent(), transactionId, -amount, -transaction.getFeeNQT());
        
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(), transactionId, amount);
        }
        applyAttachment(transaction, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(),
                transaction.getAmountNQT(), transaction.getFeeNQT());
        if (transaction.referencedTransactionFullHash() != null) {
            senderAccount.addToUnconfirmedBalanceNQT(getLedgerEvent(), transaction.getId(), 0,
                    Constants.UNCONFIRMED_POOL_DEPOSIT_NQT);
        }
    }

    abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    // isBlockDuplicate and isDuplicate share the same duplicates map, but isBlockDuplicate check is done first
    boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, boolean exclusive) {
        return isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String,Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            typeDuplicates.put(key, maxCount > 0 ? 1 : 0);
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            typeDuplicates.put(key, currentCount + 1);
            return false;
        }
        return true;
    }
    
    static boolean isDuplicateOnlyCheck(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String,Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            return false;
        }
        return true;
    }

    boolean isPruned(long transactionId) {
        return false;
    }

    public abstract boolean canHaveRecipient();

    public boolean mustHaveRecipient() {
        return canHaveRecipient();
    }

   
    Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_FEE;
    }

    Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    int getBaselineFeeHeight() {
        return 0;
    }

    int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    long[] getBackFees(Transaction transaction) {
        return Convert.EMPTY_LONG;
    }

    public abstract String getName();

    @Override
    public final String toString() {
        return getName() + " type: " + getType() + ", subtype: " + getSubtype();
    }

    public static abstract class Payment extends TransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount == null) {
                Account.getAccount(Genesis.FUCKED_TX_ID).addToBalanceAndUnconfirmedBalanceNQT(getLedgerEvent(),
                        transaction.getId(), transaction.getAmountNQT());
            }
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.ORDINARY_PAYMENT;
            }

            @Override
            public String getName() {
                return "OrdinaryPayment";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
            	if(transaction.getAttachment() != null && transaction.getAttachment() instanceof Attachment.RedeemAttachment){
            		throw new NxtException.NotValidException("Invalid attachment found");
            	}
                if (transaction.getAmountNQT() <= 0 || transaction.getAmountNQT() >= Constants.MAX_BALANCE_NQT) {
                    throw new NxtException.NotValidException("Invalid ordinary payment");
                }
            }

        };
        
        public static final TransactionType REDEEM = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_REDEEM;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.REDEEM_PAYMENT;
            }

            @Override
            public String getName() {
                return "RedeemPayment";
            }

            @Override
            Attachment.RedeemAttachment parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            	return new Attachment.RedeemAttachment(buffer, transactionVersion);
            }

            @Override
            Attachment.RedeemAttachment parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
            	return new Attachment.RedeemAttachment(attachmentData);
            }
            
           
            
            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
            	Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction.getAttachment();
            	
            	if(transaction.getFeeNQT()!=0){
                	throw new NxtException.NotValidException("You have to send a redeem TX without any fees");
                }
            	
                if (!attachment.getAddress().matches("[a-zA-Z0-9-]*")) {
                	throw new NxtException.NotValidException("Invalid characters in redeem transaction: fields.address");
                }
                
                // Check if this "address" is a valid entry in the "genesis block" claim list
                if(Redeem.hasAddress(attachment.getAddress()) == false){
                	throw new NxtException.NotValidException("You have no right to claim from genesis");
                }
                
                // Check if the amountNQT matches the "allowed" amount
                Long claimableAmount = Redeem.getClaimableAmount(attachment.getAddress());
                if(claimableAmount<=0 || claimableAmount != transaction.getAmountNQT()){
                	throw new NxtException.NotValidException("You can only claim exactly " + claimableAmount + " NQT");
                }
                
                if (!attachment.getSecp_signatures().matches("[a-zA-Z0-9+/=-]*")) {
                	throw new NxtException.NotValidException("Invalid characters in redeem transaction: fields.secp_signatures");
                }
                if (transaction.getRecipientId()==0) {
                	throw new NxtException.NotValidException("Invalid receiver ID in redeem transaction");
                }
                
                // Finally, do the costly SECP signature verification checks
                ArrayList<String> signedBy = new ArrayList<String>();
                ArrayList<String> signatures = new ArrayList<String>();
                ArrayList<String> addresses = new ArrayList<String>();
                int need = 0;
                int gotsigs = 0;
                String addy = attachment.getAddress();
                String sigs = attachment.getSecp_signatures();
                if(addy.indexOf("-")>=0){
                	String[] multiples = addy.split("-");
                	need = Integer.valueOf(multiples[0]);
                	for(int i=1;i<multiples.length;++i)
                			addresses.add(multiples[i]);
                }else{
                	need = 1;
                	addresses.add(addy);
                }
                if(sigs.indexOf("-")>=0){
                	String[] multiples = sigs.split("-");
                	gotsigs = multiples.length;
                	for(int i=0;i<multiples.length;++i)
                		signatures.add(multiples[i]);
                }else{
                	gotsigs = 1;
                	signatures.add(sigs);
                }
                
                if(signatures.size()!=need){
                	throw new NxtException.NotValidException("You have to provide exactly " + String.valueOf(need) + " signatures, you provided " + gotsigs);
                }
                
                System.out.println("Found REDEEM transaction");
                System.out.println("========================");
                String loginSig = ""; // base64 encoded signature                
                String message = "I hereby confirm to redeem " + String.valueOf(transaction.getAmountNQT()).replace("L", "") + " NQT-XEL from genesis entry " + attachment.getAddress() + " to account " + Convert.toUnsignedLong(transaction.getRecipientId()).replace("L", "");
                System.out.println("String to sign:\t" + message);
                System.out.println("We need " + String.valueOf(need) + " signatures from these addresses:");
                for (int i=0;i<addresses.size();++i)
                	System.out.println(" -> " + addresses.get(i));
                System.out.println("We got " + String.valueOf(gotsigs) + " signatures:");
                for (int i=0;i<signatures.size();++i) {
                	System.out.println(" -> " + signatures.get(i).substring(0, Math.min(12, signatures.get(i).length()))+ "...");
                	ECKey result;
					try {
						result = new ECKey().signedMessageToKey(message, signatures.get(i));
					} catch (SignatureException e) {
						throw new NxtException.NotValidException("Invalid signatures provided");
					}
					
					if(result==null)
						throw new NxtException.NotValidException("Invalid signatures provided");
					
                	String add = result.toAddress(NetworkParameters.prodNet()).toString();
                	signedBy.add(add);        
                	
                }
                System.out.println("These addresses seem to have signed:");
                for (int i=0;i<signedBy.size();++i)
                	System.out.println(" -> " + signedBy.get(i));
                
                addresses.retainAll(signedBy);
                System.out.println("We matched " + String.valueOf(need) + " signatures from these addresses:");
                for (int i=0;i<addresses.size();++i)
                	System.out.println(" -> " + addresses.get(i));
                if(addresses.size()!=need){
                	System.out.println("== " + String.valueOf(addresses.size()) + " out of " + String.valueOf(need) + " matched!");
                	throw new NxtException.NotValidException("You have to provide exactly " + String.valueOf(need) + " correct signatures");
                }
            }
            
            @Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return true;
			}
            
            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Redeem.add((TransactionImpl)transaction);
            }
            
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction.getAttachment();
                return isDuplicate(Payment.REDEEM, String.valueOf(attachment.getAddress()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.RedeemAttachment attachment = (Attachment.RedeemAttachment) transaction.getAttachment();
                boolean duplicate = isDuplicate(Payment.REDEEM, String.valueOf(attachment.getAddress()), duplicates, true) ;                
                if(duplicate == false){
                	duplicate = Redeem.isAlreadyRedeemed(attachment.getAddress());
                }
                return duplicate;
			}
        };

    }

    public static abstract class Messaging extends TransactionType {

        private Messaging() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_MESSAGING;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

       

        public static final TransactionType HUB_ANNOUNCEMENT = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.HUB_ANNOUNCEMENT;
            }

            @Override
            public String getName() {
                return "HubAnnouncement";
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingHubAnnouncement parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.MessagingHubAnnouncement(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                Hub.addOrUpdateHub(transaction, attachment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.MessagingHubAnnouncement attachment = (Attachment.MessagingHubAnnouncement) transaction.getAttachment();
                if (attachment.getMinFeePerByteNQT() < 0 || attachment.getMinFeePerByteNQT() > Constants.MAX_BALANCE_NQT
                        || attachment.getUris().length > Constants.MAX_HUB_ANNOUNCEMENT_URIS) {
                    // cfb: "0" is allowed to show that another way to determine the min fee should be used
                    throw new NxtException.NotValidException("Invalid hub terminal announcement: " + attachment.getJSONObject());
                }
                for (String uri : attachment.getUris()) {
                    if (uri.length() > Constants.MAX_HUB_ANNOUNCEMENT_URI_LENGTH) {
                        throw new NxtException.NotValidException("Invalid URI length: " + uri.length());
                    }
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }
        };
        
        public static final Messaging ACCOUNT_INFO = new Messaging() {

            private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_NXT, 2 * Constants.ONE_NXT, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                    return attachment.getName().length() + attachment.getDescription().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_INFO;
            }

            @Override
            public String getName() {
                return "AccountInfo";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_INFO_FEE;
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.MessagingAccountInfo(buffer, transactionVersion);
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.MessagingAccountInfo(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
                if (attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH
                        || attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                    throw new NxtException.NotValidException("Invalid account info issuance: " + attachment.getJSONObject());
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return isDuplicate(Messaging.ACCOUNT_INFO, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }


        };

    }

    public static abstract class AccountControl extends TransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ACCOUNT_CONTROL;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public String getName() {
                return "EffectiveBalanceLeasing";
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer, transactionVersion);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
            }

            @Override
            void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
                if (transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new NxtException.NotValidException("Account cannot lease balance to itself");
                }
                if (transaction.getAmountNQT() != 0) {
                    throw new NxtException.NotValidException("Transaction amount must be 0 for effective balance leasing");
                }
                if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                    throw new NxtException.NotValidException("Invalid effective balance leasing period: " + attachment.getPeriod());
                }
                byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                if (recipientPublicKey == null) {
                    throw new NxtException.NotCurrentlyValidException("Invalid effective balance leasing: "
                            + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new NxtException.NotValidException("Leasing to Genesis account not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

         

        };

    };

	public static abstract class WorkControl extends TransactionType {

		private WorkControl() {
		}

		@Override
		public final byte getType() {
			return TransactionType.TYPE_WORK_CONTROL;
		}

		@Override
		boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
			return true;
		}

		@Override void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
		}

		public final static TransactionType NEW_TASK = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_NEW_TASK;
			}

			@Override
			Attachment.WorkCreation parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkCreation(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkCreation parseAttachment(JSONObject attachmentData) throws NxtException.NotValidException {
		try{
				return new Attachment.WorkCreation(attachmentData);
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				try{
				Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
				Work.addWork(transaction, attachment);
				}catch(Exception e){
					e.printStackTrace();
					throw e;
				}
			}

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkCreation attachment = (Attachment.WorkCreation) transaction.getAttachment();
				
				// Immediately fail attachment validation if transaction has no SourceCode Appendix
				if(transaction.getPrunableSourceCode() == null) {
					throw new NxtException.NotValidException("Work creation transaction MUST come with a source code appendix");
				}
				
				// Now, source must not be pruned if the transaction is "young"
				if(!transaction.getPrunableSourceCode().hasPrunableData() && !(transaction.getTimestamp()<Nxt.getEpochTime()-Constants.MIN_PRUNABLE_LIFETIME)){
					throw new NxtException.NotValidException("Script kiddie, stay home! Please refrain from pruning unpruneable data");
				}
				
				// Check for correct title length
				if (attachment.getWorkTitle().length() > Constants.MAX_TITLE_LENGTH || attachment.getWorkTitle().length() < 1) {
					throw new NxtException.NotValidException("User provided POW Algorithm has incorrect title length");
		        }
				
				// Verify Deadline 
				if(attachment.getDeadline() > Constants.MAX_DEADLINE_FOR_WORK || attachment.getDeadline() < Constants.MIN_DEADLINE_FOR_WORK){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct deadline");
	        	}
				
				// Verify Bounty Limit
				if(attachment.getBountyLimit() > Constants.MAX_WORK_BOUNTY_LIMIT || attachment.getBountyLimit() < Constants.MIN_WORK_BOUNTY_LIMIT){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct bounty limit");
	        	}
				
				// Verify XEL per Pow
				if(attachment.getXelPerPow() < Constants.MIN_XEL_PER_POW){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct xel/pow price");
	        	}
				
				// Verify XEL per Bounty
				if(attachment.getXelPerBounty() < Constants.MIN_XEL_PER_BOUNTY){
					throw new NxtException.NotValidException("User provided POW Algorithm does not have a correct xel/bounty price");
	        	}
				
				// minimal payout check
				if(transaction.getAmountNQT() < Constants.PAY_FOR_AT_LEAST_X_POW*attachment.getXelPerPow() + attachment.getXelPerBounty()*attachment.getBountyLimit()){
					throw new NxtException.NotValidException("You must attach XEL for at least 20 POW submissions and all bounties, i.e., " + (Constants.PAY_FOR_AT_LEAST_X_POW*attachment.getXelPerPow()+attachment.getXelPerBounty()*attachment.getBountyLimit()) + " XEL");
				}
				
				// Measure work execution time for "new works" (avoid pointless evaluation during blockchain sync)
				if(transaction.getPrunableSourceCode().hasPrunableData() && !(transaction.getTimestamp()<Nxt.getEpochTime()-Constants.EVAL_WORK_EXEC_TIME_AGE_SECONDS)){
					
					byte[] source = transaction.getPrunableSourceCode().getSource();
					long wid = transaction.getId();
					GigaflopEstimator.measure_and_store_source(wid, source);
							
				}
				
				
				
				
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean specialDepositTX() {
				return true;

			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_CREATION;
			}

			@Override
			public String getName() {
				return "WorkCreation";
			}

		};

		public final static TransactionType CANCEL_TASK_REQUEST = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_CANCEL_TASK_REQUEST;
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(buffer, transactionVersion);
			}

			@Override
			Attachment.WorkIdentifierCancellationRequest parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.WorkIdentifierCancellationRequest(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				Work.getWork(attachment.getWorkId()).natural_timeout(transaction.getBlock());
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
				
				
                Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction.getAttachment();
                return isDuplicate(WorkControl.CANCEL_TASK_REQUEST, String.valueOf(attachment.getWorkId()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction.getAttachment();
                boolean duplicate = isDuplicate(WorkControl.CANCEL_TASK_REQUEST, String.valueOf(attachment.getWorkId()), duplicates, true) ;                
                
                if(duplicate == false){
                	Work w = Work.getWorkByWorkId(attachment.getWorkId());
                	if(w.isClosed()) return true;
                	if(w.isClose_pending()) return true;
                }
                
                return duplicate;
            }

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.WorkIdentifierCancellationRequest attachment = (Attachment.WorkIdentifierCancellationRequest) transaction
						.getAttachment();
				
				Work w = Work.getWorkByWorkId(attachment.getWorkId());
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist yet");
				}
				

				if(w.isClosed()){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " is already closed");
				}
				
				if (w.getSender_account_id() != transaction
						.getSenderId()) {
					throw new NxtException.NotValidException("Only the work creator can cancel this work");
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			@Override
			public boolean moneyComesFromNowhere() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_CANCELLATION_REQUEST;
			}

			@Override
			public String getName() {
				return "WorkIdentifierCancellationRequest";
			}
		};

		public final static TransactionType PROOF_OF_WORK = new WorkControl() {

			private LRUCache soft_unblock_cache = new LRUCache(50);
			
			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_PROOF_OF_WORK;
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfWork parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfWork(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
				
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
				PowAndBounty.addPow(transaction, attachment);
				PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				obj.applyPowPayment(transaction.getBlock());
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction.getAttachment();
                return isDuplicate(WorkControl.PROOF_OF_WORK, Convert.toHexString(attachment.getHash()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction.getAttachment();
                boolean duplicate = isDuplicate(WorkControl.PROOF_OF_WORK, Convert.toHexString(attachment.getHash()), duplicates, true);
                if(duplicate == false){
		        	// This is required to limit the amount of unconfirmed POWs to not exceed either the money or the hard limit per block.
		        	Work w = Work.getWork(attachment.getWorkId());
		        	
		        	if(w.isClose_pending()) {transaction.setExtraInfo("work already closed"); return true; }
		        	if(w.isClosed())  {transaction.setExtraInfo("work already closed"); return true; }
		        	
		        	long bal_fund = w.getBalance_pow_fund();
		        	long xel_per_pow = w.getXel_per_pow();
		        	long how_many_left = Math.floorDiv(bal_fund, xel_per_pow);
		        	int left = Integer.MAX_VALUE;
		        	if(how_many_left < left){
		        		left = (int)how_many_left;
		        	}
		        	
		        	boolean soft_throttling = false;
		        	if(left >Constants.MAX_POWS_PER_BLOCK){
		        		soft_throttling = true;
		        		left = Constants.MAX_POWS_PER_BLOCK;
		        	}
		        	
		        	if(how_many_left<=0){
		        		if(soft_throttling)
		        			transaction.setExtraInfo("maximum pows per block reached");
		        		else
		        			transaction.setExtraInfo("work ran out of funds");
		        		duplicate = true;
		        	}else{
			        	duplicate = isDuplicate(WorkControl.PROOF_OF_WORK, String.valueOf(attachment.getWorkId()), duplicates, left);
		        	}
		        }
		        return duplicate;
            }

			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				
				if(transaction.getDeadline() != 3){
					throw new NxtException.NotValidException("POW/Bounties must have a dead line of 3 minutes");
				}
				
				Attachment.PiggybackedProofOfWork attachment = (Attachment.PiggybackedProofOfWork) transaction
						.getAttachment();
						
				Work w = Work.getWorkByWorkId(attachment.getWorkId());	
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " does not exist");
				}
				
				byte[] hash = attachment.getHash();
				if(PowAndBounty.hasHash(attachment.getWorkId(), hash)){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " already has this submission, dropping duplicate");
				}
				
				BigInteger real_block_target = w.getWork_min_pow_target_bigint();
				
				POW_CHECK_RESULT valid = POW_CHECK_RESULT.ERROR;
								
				if(PrunableSourceCode.isPrunedByWorkId(attachment.getWorkId())){
					// If the tx is already pruned AND the transaction timestamp is old enough, we assume POW is valid!
					// no need to execute after all! We assume that the pruning is happened long enough ago
					// This is valid, because MIN_PRUNABLE_LIFETIME is longer than the maximum work timeout length! We are just catching up the blockchain here, not actually "submitting live work"
					if(transaction.getTimestamp()<Nxt.getEpochTime()-Constants.MIN_PRUNABLE_LIFETIME)
						valid = POW_CHECK_RESULT.OK;
					else
						// Otherwise a script kiddie is trying to be an uber haxx0r:
						throw new NxtException.NotValidException(
								"Proof of work is invalid: references a pruned source code when it should not be pruned");
					
					
				}else{
					PrunableSourceCode code = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(attachment.getWorkId());
					try {
						valid = Executioner.executeProofOfWork(code.getSource(),attachment.personalizedIntStream(transaction.getSenderPublicKey(), w.getBlock_id()), real_block_target, real_block_target /* deprecated soft unblock */);
					} catch (Exception e1) {
						e1.printStackTrace();
						throw new NxtException.NotCurrentlyValidException(
								"Proof of work is invalid: causes ElasticPL function to crash");
					}
					if (valid == POW_CHECK_RESULT.ERROR) {
						throw new NxtException.LostValidityException(
								"Proof of work is invalid: does not anylonger meet target " + real_block_target.toString(16) + " for work_id = " + Convert.toUnsignedLong((w.getWork_id())));
					}
				}
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			public boolean moneyComesFromNowhere() {
				return true;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_POW;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfWork";
			}

		};
		public final static TransactionType BOUNTY_ANNOUNCEMENT = new WorkControl() {
		
			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY_ANNOUNCEMENT;
			}
		
			@Override
			Attachment.PiggybackedProofOfBountyAnnouncement parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBountyAnnouncement(buffer, transactionVersion);
			}
		
			@Override
			Attachment.PiggybackedProofOfBountyAnnouncement parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBountyAnnouncement(attachmentData);
			}
		
			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
		
				Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				PowAndBountyAnnouncements obj = PowAndBountyAnnouncements.addBountyAnnouncement(transaction, attachment);
				obj.applyBountyAnnouncement(transaction.getBlock());
			}
			
			@Override
		    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
		        Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction.getAttachment();
		        return isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT, Convert.toHexString(attachment.getHashAnnounced()), duplicates, true);
		    }
			
			@Override
		    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
		        Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction.getAttachment();
		        boolean duplicate = isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT, Convert.toHexString(attachment.getHashAnnounced()), duplicates, true);
		        if(duplicate == false){
		        	// This is required to limit the amount of unconfirmed BNT Announcements to not exceed the requested bounty # by the requester.
		        	// But first, check out how many more we want from what has been already confirmed!
		        	Work w = Work.getWork(attachment.getWorkId());
		        	
		        	if(w.isClose_pending()) return true;
		        	if(w.isClosed()) return true;
		        	
		        	int count_wanted = w.getBounty_limit();
		        	int count_has_announcements = w.getReceived_bounty_announcements();
		        	int left_wanted = count_wanted-count_has_announcements;
		        	if(left_wanted<=0){
		        		transaction.setExtraInfo("no more bounty announcement slots available");
		        		duplicate = true;
		        	}else{
			        	duplicate = isDuplicate(WorkControl.BOUNTY_ANNOUNCEMENT, String.valueOf(attachment.getWorkId()), duplicates, left_wanted);
		        	}
		        }
		        return duplicate;
		    }
			
		
			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.PiggybackedProofOfBountyAnnouncement attachment = (Attachment.PiggybackedProofOfBountyAnnouncement) transaction
						.getAttachment();
				Account acc = Account.getAccount(transaction.getSenderId());
				if(acc == null || acc.getGuaranteedBalanceNQT(1, Nxt.getBlockchain().getHeight()) < Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION){
					throw new NxtException.NotValidException("You cannot cover the " + Constants.DEPOSIT_BOUNTY_ACCOUNCEMENT_SUBMISSION + " NQT deposit fee for your bounty announcement.");
				}
						
				Work w = Work.getWorkByWorkId(attachment.getWorkId());
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + attachment.getWorkId() + " does not exist");
				}
				
				if(PrunableSourceCode.isPrunedByWorkId(w.getId())){
					// If the tx is already pruned AND the transaction timestamp is old enough, we assume submission is valid!
					// no need to execute after all! We assume that the pruning is happened long enough ago
					// This is valid, because MIN_PRUNABLE_LIFETIME is longer than the maximum work timeout length! We are just catching up the blockchain here, not actually "submitting live work"
					if(!(transaction.getTimestamp()<Nxt.getEpochTime()-Constants.MIN_PRUNABLE_LIFETIME))
						throw new NxtException.NotValidException(
								"Bounty announcement is invalid: references a work package with pruned source code when it should not be pruned");
				}
				
				
				byte[] hash = attachment.getHashAnnounced();
				if(PowAndBountyAnnouncements.hasHash(attachment.getWorkId(), hash)){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " already has this submission, dropping duplicate");
				}
		
			}
		
			@Override
			public boolean canHaveRecipient() {
				return false;
			}
		
			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}
		
			@Override
			public boolean mustHaveRecipient() {
				return false;
			}
		
			public boolean moneyComesFromNowhere() {
				return false;
			}
		
			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_BOUNTY_ANNOUNCEMENT;
			}
		
			@Override
			public String getName() {
				return "PiggybackedProofOfBountyAnnouncement";
			}
		};

		public final static TransactionType BOUNTY = new WorkControl() {

			@Override
			public final byte getSubtype() {
				return TransactionType.SUBTYPE_WORK_CONTROL_BOUNTY;
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(ByteBuffer buffer, byte transactionVersion)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(buffer, transactionVersion);
			}

			@Override
			Attachment.PiggybackedProofOfBounty parseAttachment(JSONObject attachmentData)
					throws NxtException.NotValidException {
				return new Attachment.PiggybackedProofOfBounty(attachmentData);
			}

			@Override
			void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {

				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				PowAndBounty.addBounty(transaction, attachment);
				PowAndBounty obj = PowAndBounty.getPowOrBountyById(transaction.getId());
				obj.applyBounty(transaction.getBlock());
			}
			
			@Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction.getAttachment();
                return isDuplicate(WorkControl.BOUNTY, Convert.toHexString(attachment.getHash()), duplicates, true);
            }
			
			@Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction.getAttachment();
                boolean duplicate = isDuplicate(WorkControl.BOUNTY, Convert.toHexString(attachment.getHash()), duplicates, true);
		        if(duplicate == false){
		        	// This is required to limit the amount of unconfirmed BNT Announcements to not exceed the requested bounty # by the requester.
		        	// But first, check out how many more we want from what has been already confirmed!
		        	Work w = Work.getWork(attachment.getWorkId());
		        	
		        	if(w.isClosed()){
		        		transaction.setExtraInfo("work is already closed, you missed the reveal period");
		        		return true;
		        	}
		        	
		        }
		        return duplicate;
            }
			
	
			@Override
			void validateAttachment(Transaction transaction) throws NxtException.ValidationException {
				Attachment.PiggybackedProofOfBounty attachment = (Attachment.PiggybackedProofOfBounty) transaction
						.getAttachment();
				
						
				Work w = Work.getWorkByWorkId(attachment.getWorkId());
				
				if(w==null){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " does not exist");
				}
				
				
				
				// check if we had an announcement for this bounty earlier
				boolean hadAnnouncement = PowAndBountyAnnouncements.hasValidHash(attachment.getWorkId(), attachment.getHash());
				if(!hadAnnouncement){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " has not yet seen a \"counted\" bounty announcement for this submission with work_id " + attachment.getWorkId() + ", hash " + (new BigInteger(attachment.getHash()).toString(16)) + " and multi " + (new BigInteger(attachment.getMultiplicator()).toString(16)));
				}
				
				byte[] hash = attachment.getHash();
				if(PowAndBounty.hasHash(attachment.getWorkId(), hash)){
					throw new NxtException.NotCurrentlyValidException("Work " + Convert.toUnsignedLong(attachment.getWorkId()) + " already has this submission, dropping duplicate");
				}
				
				long rel_id = transaction.getBlockId();
				boolean valid = false;
				
				if(PrunableSourceCode.isPrunedByWorkId(attachment.getWorkId())){
					// If the tx is already pruned AND the transaction timestamp is old enough, we assume POW is valid!
					// no need to execute after all! We assume that the pruning is happened long enough ago
					// This is valid, because MIN_PRUNABLE_LIFETIME is longer than the maximum work timeout length! We are just catching up the blockchain here, not actually "submitting live work"
					if(transaction.getTimestamp()<Nxt.getEpochTime()-Constants.MIN_PRUNABLE_LIFETIME)
						valid = true;
					else
						// Otherwise a script kiddie is trying to be an uber haxx0r:
						throw new NxtException.NotValidException(
								"Bounty is invalid: references a pruned source code when it should not be pruned");
				}else{
					PrunableSourceCode code = nxt.PrunableSourceCode.getPrunableSourceCodeByWorkId(attachment.getWorkId());
					try {
						valid = Executioner.executeBountyHooks(code.getSource(), attachment.personalizedIntStream(transaction.getSenderPublicKey(), w.getBlock_id()));
					} catch (Exception e1) { 
						e1.printStackTrace();
						throw new NxtException.NotCurrentlyValidException(
								"Bounty is invalid: causes ElasticPL function to crash");
					}
					if (!valid) {
						System.err.println("POW was not valid!!");
						throw new NxtException.NotValidException(
								"Bounty is invalid: does not meet requirement");
					}
				}
				
			}

			@Override
			public boolean canHaveRecipient() {
				return false;
			}

			@Override
			public boolean zeroFeeTransaction() {
				return true;
			}

			@Override
			public boolean mustHaveRecipient() {
				return false;
			}

			public boolean moneyComesFromNowhere() {
				return false;
			}

			@Override
			public LedgerEvent getLedgerEvent() {
				return LedgerEvent.WORK_BOUNTY;
			}

			@Override
			public String getName() {
				return "PiggybackedProofOfBounty";
			}
		};
		
	};

}
