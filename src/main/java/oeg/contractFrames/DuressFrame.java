/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package oeg.contractFrames;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Maria
 */
public class DuressFrame {

    public DuressFrame() {
    }
    
    public String manifester = "";
    public String manifestee = "";
    public String date = "";
    public String action = "";

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
    
    public boolean isEmpty() {
        return (manifester.isEmpty() && manifestee.isEmpty() && date.isEmpty());
    }
    
//    public String toProleg() {
//        String outp = "agreement_of_purchase_contract(" + manifester + "," + manifestee + "," + convertDate(date) + ").\n";
//        return outp;
//    }
    
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

    public String getManifester() {
        return manifester;
    }

    public void setManifester(String manifester) {
        this.manifester = manifester;
    }

    public String getManifestee() {
        return manifestee;
    }

    public void setManifestee(String manifestee) {
        this.manifestee = manifestee;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
    
    
}
