package oeg.contractFrames;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to represent a purchase. 
 * 
 * @author Maria
 */
public class PurchaseFrame {

    public PurchaseFrame() {
    }
    
    private String buyer = "";
    private String seller = "";
    private String item = "";
    private String price = "";
    private String currency = "";
    private String date = "";
    private String contract = "";
    
    public boolean isEmpty() {
        return (buyer.isEmpty() && seller.isEmpty() && item.isEmpty() && price.isEmpty() && date.isEmpty() && contract.isEmpty());
    }
    
    public String toProleg() {
        String pr = price;
        if(price.isEmpty()){
            pr = "price";
        }
        String outp = "agreement_of_purchase_contract(" + buyer + "," + seller + "," + item + "," + pr + "," + convertDate(date) + "," + contract + ").\n";
        return outp;
    }
    
    public String convertDate(String input){
        String outp = "";
        if(input.isEmpty()){
            return "";
        }
        Pattern pTE = Pattern.compile("(\\d+)-(\\w+)-(\\d+)");
        Matcher mTE = pTE.matcher(input);
        if (mTE.find()) {
            outp = mTE.group(1) + " year " + mTE.group(2) + " month " + mTE.group(3) + " day";
        }
        return outp;
    }
    
    /**
     * @return the buyer
     */
    public String getBuyer() {
        return buyer;
    }

    /**
     * @param buyer the buyer to set
     */
    public void setBuyer(String buyer) {
        this.buyer = buyer;
    }

    /**
     * @return the seller
     */
    public String getSeller() {
        return seller;
    }

    /**
     * @param seller the seller to set
     */
    public void setSeller(String seller) {
        this.seller = seller;
    }

    /**
     * @return the item
     */
    public String getItem() {
        return item;
    }

    /**
     * @param item the item to set
     */
    public void setItem(String item) {
        this.item = item;
    }

    /**
     * @return the price
     */
    public String getPrice() {
        return price;
    }

    /**
     * @param price the price to set
     */
    public void setPrice(String price) {
        this.price = price;
    }

    /**
     * @return the currency
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * @param currency the currency to set
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * @return the date
     */
    public String getDate() {
        return date;
    }

    /**
     * @param date the date to set
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * @return the contract
     */
    public String getContract() {
        return contract;
    }

    /**
     * @param contract the contract to set
     */
    public void setContract(String contract) {
        this.contract = contract;
    }
    
}
