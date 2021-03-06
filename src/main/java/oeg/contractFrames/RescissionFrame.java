package oeg.contractFrames;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static oeg.contractFrames.PurchaseFrame.CF;
import static oeg.contractFrames.PurchaseFrame.DATA;

/**
 * Class to represent the recission of a contract.
 * @author Maria
 */
public class RescissionFrame {

    public RescissionFrame() {
    }
    
    private String manifester = "";
    private String manifestee = "";
//    private String item = "";
    private String date = "";
    private String action = "";
    public DuressFrame duress = new DuressFrame();
    
    public boolean isEmpty() {
        return (manifester.isEmpty() && manifestee.isEmpty() && date.isEmpty() && action.isEmpty() && duress.isEmpty());
    }
    
    public static String CF = "https://mnavasloro.github.io/ContractFrames/";
    public static String DATA = "https://mnavasloro.github.io/ContractFrames/data/";
    public static String MCO = "http://purl.oclc.org/NET/mco-core/";
    public String toRDF()
    {
        String rdf="";
        UUID uuid = UUID.randomUUID();
        String agp = DATA+uuid;
        
       rdf+="<"+ agp +"> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <"+CF+"Manifestation> . \n" ;
       rdf+="<"+ agp +"> <"+ CF+"manifester" +" > \""+manifester+"\" . \n" ;
       rdf+="<"+ agp +"> <"+ CF+"manifestee" +" > \""+manifestee+"\" . \n" ;
       rdf+="<"+ agp +"> <"+ CF+"rescission" +" > \""+action+"\" . \n" ;
       rdf+="<"+ agp +"> <http://purl.org/dc/terms/date> \""+convertDate(date) + "\" . \n" ;
        if(!duress.isEmpty()){
            String dur = DATA+uuid;
            rdf+="<"+ dur +"> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <"+CF+"Duress> . \n" ;
            rdf+="<"+ dur +"> <"+ CF+"manifester" +" > \""+duress.getManifester()+"\" . \n" ;
            rdf+="<"+ dur +"> <"+ CF+"manifestee" +" > \""+duress.getManifestee() +"\" . \n" ;
            rdf+="<"+ dur +"> <http://purl.org/dc/terms/date> \""+convertDate(duress.date) + "\" . \n" ;
            rdf+="<"+ agp +"> <"+ CF+"rescission" +" > \""+action+"\" . \n" ;
        }
        return rdf;
    }
    public String toProleg() {
        String outp = "manifestation_fact(rescission(" + action + ")," + manifester + "," + manifestee + "," + convertDate(date) + ").\n";
        if(!duress.isEmpty()){
            outp = outp + "fact_of_duress(" + duress.getManifester() + "," + duress.getManifestee() + ",rescission(" + action + ")," + convertDate(duress.date) + ").\n";
        }
        return outp;
    }
    
    public String convertDate(String input){
        String outp = input;
//        String outp = "";
        if(input.isEmpty()){
            return "";
        }
        Pattern pTE = Pattern.compile("(\\d+)-(\\w+)-(\\d+)");
        Matcher mTE = pTE.matcher(input);
        if (mTE.find()) {
            outp = mTE.group(1) + " year " + mTE.group(2) + " month " + mTE.group(3) + " day";
        }
        
//        pTE = Pattern.compile("OFFSET (\\d+)([A-Z])");
//        mTE = pTE.matcher(input);
//        if (mTE.find()) {
//            outp = mTE.group(1) + " year " + mTE.group(2) + " month " + mTE.group(3) + " day";
//        }
        
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public DuressFrame getDuress() {
        return duress;
    }

    public void setDuress(DuressFrame duress) {
        this.duress = duress;
    }
    
    public String toResc(){
        return "rescission(" + action + ")";
    }
    
}
