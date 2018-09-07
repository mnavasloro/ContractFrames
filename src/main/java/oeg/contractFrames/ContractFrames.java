package oeg.contractFrames;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;

/**
 * Class with the methods to annotate English texts referring to contracts. 
 * The output of this method is:
 * - A set of frames derived from the text
 * - PROLEG expressions
 * 
 * @author mnavas
 */
public class ContractFrames {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ContractFrames.class);

    StanfordCoreNLP pipeline;
    PrintWriter out;

    Map<String, String> corefsubs = new HashMap<String, String>();

    /* Variables for dependency */
    String modelPath = DependencyParser.DEFAULT_MODEL;
    String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
    MaxentTagger tagger = new MaxentTagger(taggerPath);
    DependencyParser parser = DependencyParser.loadFromModelFile(modelPath);

    /* Variables for preprocessing */
    StanfordCoreNLP preprocessPipeline;
    
    /* Variables for logicoutput */
    Map<String,String> refItem = new HashMap<String,String>();
    List<String> logical = new ArrayList<String>();
    List<PurchaseFrame> framesPurchase = new ArrayList<PurchaseFrame>();
    List<RescissionFrame> framesRescission = new ArrayList<RescissionFrame>();
    List<DuressFrame> framesDuress = new ArrayList<DuressFrame>();
    
    public int numindex = 1;

    /**
     * Initializes a instance of the tagger
     *
     */
    public ContractFrames() {
        init();
    }

    /**
     *
     */
    public void init() {

        /* Path to the file with the rules */
        String rules = "../ContractFrames/src/main/resources/rules/rulesENframesContract.txt";

        out = new PrintWriter(System.out);

        Properties properties = new Properties();
        properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,tokensregexdemo");
        properties.setProperty("customAnnotatorClass.tokensregexdemo", "edu.stanford.nlp.pipeline.TokensRegexAnnotator");
        properties.setProperty("tokensregexdemo.rules", rules);
        System.out.println(properties);

        /* Initialization of CoreNLP pipeline */
        pipeline = new StanfordCoreNLP(properties);

        /* Initialization of the preprocessing pipeline */
        Properties propertiesPreproc = new Properties();
        propertiesPreproc.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        preprocessPipeline = new StanfordCoreNLP(propertiesPreproc);
    }

    //TODO: change output to string, so it returns the annotated string
    /**
     * Annotates a String text
     *
     * @param text
     *
     * EXAMPLES OF INPUT text: "The contract entered into force yesterday. The
     * establishment of the purchase contract was done the day after."
     *
     * "The contract entered into force yesterday."
     *
     * "A established a purchase contract with B to buy a land, L on April 1,
     * 2018. The establishment of the contract wasn't done the next day. A
     * rescinded the purchase contract by a fraud by the thrid party C on May 1,
     * 2018."
     *
     * "A established a purchase contract with B to buy a land, L on April"
     * @return Annotated String in PROLEG
     *
     */
    public String annotate(String text) {

        String output = text;
        output = preprocessInput(output);

        Annotation annotation = new Annotation(output);
        pipeline.annotate(annotation);

        int offset = 0;
        int offsetAddEst = "<EVENTESTABLISH>".length() + "</EVENTESTABLISH>".length();
        int offsetAddEnd = "<EVENTEND>".length() + "</EVENTEND>".length();
        int offsetAddSell = "<PURCHASESELL>".length() + "</PURCHASESELL>".length();
        int offsetAddBuy = "<PURCHASEBUY>".length() + "</PURCHASEBUY>".length();
        int offsetAddDur = "<DURESS>".length() + "</DURESS>".length();
        out.println();
        out.println(annotation.toShorterString());
        Timex previous = null;
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            // DEPENDENCY
            String deppar = dependencyParsing(sentence.toString());
            PurchaseFrame frameBuy = new PurchaseFrame();
            RescissionFrame frameRescission = new RescissionFrame();
            DuressFrame frameDuress = new DuressFrame();
            String value = "";
            String price = "";
            String contract = "";
            String rescission = "";
            
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                /* We collect the different tags of each token */
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                String normalized = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
                String contractEvent = token.get(contractEvents.ContractEventTagAnnotation.class);
                String date = token.get(contractEvents.DateTagAnnotation.class);
                Timex timex = token.get(TimeAnnotations.TimexAnnotation.class);
                if (contractEvent != null) {
                    /* Verb Event */
                    if (contractEvent.contains("establish") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the event is not negated
                        frameBuy = checkPurchaseEstablish(deppar, word, frameBuy);
                        token.setNER("eventEstablish");
                        output = output.substring(0, offset + token.beginPosition()) + "<EVENTESTABLISH>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</EVENTESTABLISH>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddEst;
                    } else if (contractEvent.contains("end") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the event is not negated
                        frameRescission = checkEnd(deppar, word, frameRescission);
                        token.setNER("eventEnd");
                        output = output.substring(0, offset + token.beginPosition()) + "<EVENTEND>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</EVENTEND>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddEnd;
                        rescission = frameRescission.toResc();
                    } /* Noun Event */ else if (contractEvent.contains("nounEstablish") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the event is not negated
                        frameBuy = checkPurchaseEstablish(deppar, word, frameBuy);
                        token.setNER("eventEstablish");
                        output = output.substring(0, offset + token.beginPosition()) + "<EVENTESTABLISH>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</EVENTESTABLISH>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddEst;
                    } else if (contractEvent.contains("nounEnd") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the event is not negated
                        frameRescission = checkNounEnd(deppar, word, frameRescission);
                        token.setNER("eventEnd");
                        output = output.substring(0, offset + token.beginPosition()) + "<EVENTEND>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</EVENTEND>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddEnd;
                        rescission = frameRescission.toResc();
                    } /* Noun Event */ else if (contractEvent.contains("purchasebuy") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the buy is not negated
                        frameBuy = checkPurchaseBuy(deppar, word, frameBuy);
                        token.setNER("purchasebuy");
                        output = output.substring(0, offset + token.beginPosition()) + "<PURCHASEBUY>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</PURCHASEBUY>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddBuy;
                    } else if (contractEvent.contains("purchasesell") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the sale is not negated
                        frameBuy = checkPurchaseSell(deppar, word, frameBuy);
                        token.setNER("purchasesell");
                        output = output.substring(0, offset + token.beginPosition()) + "<PURCHASESELL>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</PURCHASESELL>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddSell;
                    }
                    else if (contractEvent.contains("threaten") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the threat is not negated
                        frameDuress = checkThreat(deppar, word, frameDuress);
                        token.setNER("duress");
                        output = output.substring(0, offset + token.beginPosition()) + "<DURESS>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</DURESS>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddDur;
                    }
                    else if (contractEvent.contains("duress") && !checkNeg(deppar, word) && !checkXCOMP(deppar, word)) { // We check the threat is not negated
                        frameDuress = checkDuress(deppar, word, frameDuress);
                        token.setNER("duress");
                        output = output.substring(0, offset + token.beginPosition()) + "<DURESS>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</DURESS>" + output.substring(offset + token.endPosition());
                        offset = offset + offsetAddDur;
                    }
                }
                if (ne.contains("CONTRACT")) {
                    contract = word;
                    output = output.substring(0, offset + token.beginPosition()) + "<CONTRACT>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</CONTRACT>" + output.substring(offset + token.endPosition());
                    offset = offset + "<CONTRACT>".length() + "</CONTRACT>".length();
                }
                if (ne.contains("MONEY")) {
                    price = normalized;
                    output = output.substring(0, offset + token.beginPosition()) + "<PRICE>" + output.substring(offset + token.beginPosition(), offset + token.endPosition()) + "</PRICE>" + output.substring(offset + token.endPosition());
                    offset = offset + "<PRICE>".length() + "</PRICE>".length();
                }

                if (timex != null && (previous == null || timex != previous)) {
                    value = timex.value();
                    if(value==null){
                        value=timex.altVal();
                    }
                    if(value==null){
                        value="";
                    }
                    int prevoutput = output.length();
                    String tag = timex.toString();
                    String aux1 = output.substring(offset + token.beginPosition());
                    String inthetag = tag.replaceAll("<[^>]*>", "");
                    String aux = aux1.replaceFirst(inthetag, tag);
                    output = output.substring(0, offset + token.beginPosition()) + aux;// + normalized + "\">"+output.substring(offset+token.beginPosition(),offset+token.endPosition())+"</DATE>"+output.substring(offset+token.endPosition());                        
                    offset = offset + (output.length() - prevoutput);
                    previous = timex;
                }
                if (timex == null && previous != null) {
                    previous = timex;
                }
            out.println("token: word=" + word + ",  \t lemma=" + lemma + ",  \t pos=" + pos + ",  \t ne=" + ne + ",  \t normalized=" + normalized + ",  \t contractEv=" + contractEvent + ",  \t date=" + date);
            
            }
             if(!frameBuy.isEmpty()){
                if(!value.isEmpty()){
                    frameBuy.setDate(value);
                }
                if(!price.isEmpty()){
                    frameBuy.setPrice(price.replaceAll("[^\\d\\.,]", "").replaceAll("[,.]0+", ""));
                    frameBuy.setCurrency(price.replaceAll("[\\d\\.,]", ""));
                }
                if(!contract.isEmpty() && frameBuy.getContract().isEmpty()){
                    frameBuy.setContract(contract);
                }
                framesPurchase.add(frameBuy);
            }
            if(!frameRescission.isEmpty()){
                if(!value.isEmpty()){
                    frameRescission.setDate(value);
                }
                if(!contract.isEmpty() && frameRescission.getAction().isEmpty()){
                    frameBuy.setContract(contract);
                }
                framesRescission.add(frameRescission);
            }
            if(!frameDuress.isEmpty()){
                if(!value.isEmpty()){
                    frameDuress.setDate(value);
                }
                if(!rescission.isEmpty() && !rescission.equals("rescission()") && frameDuress.getAction().isEmpty()){
                    frameDuress.setAction(rescission);
                    int i = 0;
                    while(i < framesRescission.size()){
                        RescissionFrame f = framesRescission.get(i);
                        if(f.toResc().equals(rescission)){
                            f.setDuress(frameDuress);
                            framesRescission.set(i, f);
                            i = framesRescission.size()+1;
                        }
                        else{
                            i++;
                        }
                    }
                    if(i == framesRescission.size()){
                        framesDuress.add(frameDuress);
                    }
                }
                else{
                int i = 0;
                    while(i < framesRescission.size()){
                        RescissionFrame f = framesRescission.get(i);
                        if(f.duress.isEmpty()){
                            frameDuress.setAction(f.toResc());
                            f.setDuress(frameDuress);
                            framesRescission.set(i, f);
                            i = framesRescission.size()+1;
                        }
                        else{
                            i++;
                        }
                    }
                    if(i == framesRescission.size()){
                        framesDuress.add(frameDuress);
                    }
                }
                    
            }
        }
        out.flush();


        /* We create a nlp.xml file as output, with the tags per token and parse trees/constituents */
        try {
            FileOutputStream os = new FileOutputStream(new File("nlp" + numindex + ".xml"));
            pipeline.xmlPrint(annotation, os);
        } catch (Exception ex) {
            Logger.getLogger(ContractFrames.class.getName()).log(Level.SEVERE, null, ex);
        }
        output = output.replaceAll("</EVENTESTABLISH> <EVENTESTABLISH>", " ");
        output = output.replaceAll("</EVENTEND> <EVENTEND>", " ");
        output = output.replaceAll("</PURCHASEBUY> <PURCHASEBUY>", " ");
        output = output.replaceAll("</PURCHASESELL> <PURCHASESELL>", " ");
        output = output.replaceAll("<CONTRACT><CONTRACT>", "<CONTRACT>");
        output = output.replaceAll("</CONTRACT></CONTRACT>", "</CONTRACT>");
        output = output.replaceAll("</MONEY></MONEY>", "</MONEY>");

//        if (!writeFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<TEXT>\n" + output + "\n</TEXT>", "outputOR" + numindex + ".xml")) {
//            System.err.println("ERROR WHILE SAVING AS INLINE IN outputOR" + numindex + ".xml");
//        }

        String value = "";
        // We check the relations:
        // 1. event-contract
        // 2. event-TE

        // 2. event-TE
        String[] output2 = output.split("\\.");
        for (String s : output2) {
            String deppar = dependencyParsing(s.replaceAll("<[^>]*>", ""));
            if (s.contains("<TIMEX") && s.contains("<EVENT")) {
                Pattern pTE = Pattern.compile("<TIMEX3 [^>]+>[^<]+</TIMEX3>");
                Matcher mTE = pTE.matcher(s);
                while (mTE.find()) {
                    String te = mTE.group(0);
                    Pattern pEv = Pattern.compile("<EVENT[^>]*>([^<]+)</EVENT");
                    Matcher mEv = pEv.matcher(s);
                    while (mEv.find()) {
                        value = checkRelationEventTE(deppar, mEv.group(1), te);
                        if (!value.equals("")) {
                            output = output.replace(te, "$DATE" + value);
                        }
                    }
                }
            }

        }

        output = writeProlegFile("proleg" + numindex + ".txt");

        numindex++;
        
        cleanGarb();
        return output;
    }

    /**
     *
     * @param text one sentence (if not, it will just return the result for the
     * first sentcene)
     * @return String with the dependency parsing
     */
    public String dependencyParsing(String text) {

        DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(text));
        for (List<HasWord> sentence : tokenizer) { 
            List<TaggedWord> tagged = tagger.tagSentence(sentence);
            GrammaticalStructure gs = parser.predict(tagged);
            return gs.typedDependenciesEnhancedPlusPlus().toString();
        }

        return "";
    }

    /**
     *
     * @param text input text
     * @param annotation with information about the tree
     * @return String with the parsing tree
     */
    public String constituencyParsing(String text, Annotation annotation) {

        System.out.println("\n\n----------------\n");
        Tree tree2 = annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class);
        System.out.println(tree2);
        System.out.println("\n\n----------------\n");
        return tree2.toString();
    }

    /**
     *
     * @param constituency the contituency tree of the string to annotate
     * @param output the string to annotate
     * @return String with the output annotated
     */
    public String constituencyContract(String constituency, String output) {

        Pattern pContract = Pattern.compile("\\(NP ((\\([^)]+\\) )*\\(NN contract\\)( \\([^)]+\\))*)\\)");
        Matcher mText = pContract.matcher(constituency);
        while (mText.find()) {
            String outp = mText.group(1);
            while (outp.contains(")")) {
                outp = outp.replaceAll("\\(\\w+ (\\w+)\\)", "$1");
            }
            output = output.replaceFirst(outp, "<CONTRACT>" + outp + "</CONTRACT>");
        }
        return output;
    }

    /**
     *
     * @param text text to mark up mentions to contracts
     * @param annotation with information about coref
     * @return String with the mentions to contracts marked up
     */
    public String corefParsing(String text, Annotation annotation) {
        String output = text;

        int ids = 0;
        for (CorefChain cc : annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            String current = "";
            for (CorefChain.CorefMention aux : cc.getMentionsInTextualOrder()) {
                String[] out = preprocessACoref(aux.mentionSpan, output, ids, current);
                output = out[0];
                if (!out[1].equals("") && !current.equals(out[1])) {
                    current = out[1];
                }
            }

        }

        System.out.println("---");
        System.out.println("coref chains");
        for (CorefChain cc : annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            System.out.println("\t" + cc);
        }
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("---");
            System.out.println("mentions");
            for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
                System.out.println("\t" + m);
            }
        }

        return output;
    }

    /**
     *
     * @param text text to mark up mentions to contracts
     * @param annotation with information about coref
     * @return String with the mentions to contracts marked up
     */

    /**
     * We check if there are any negations in the scope of the word (an event)
     *
     * @param deppar: dependency parsing string
     * @param word: the word to which the
     * @param te: temporal expression
     * @return boolean true: if the event is negated false: if there is no
     * negation in the scope
     */
    public String checkRelationEventTE(String deppar, String word, String te) {

        String value = "";
        String inTimex = "";

        Pattern pText1 = Pattern.compile("<TIMEX3[^>]* value=\"([^\"]*)\"[^>]*>([^<]+)<");
        Matcher mText1 = pText1.matcher(te);
        if (mText1.find()) {
            value = mText1.group(1);
            inTimex = mText1.group(2);
        } else {
            System.out.println("NO VALUE FOUND FOR THE TIMEX");
            return "";
        }

        //TOKENIZE WORDS AND KEEP THE VALUE
        Annotation annotation = new Annotation(inTimex);
        preprocessPipeline.annotate(annotation);
        CoreMap firstSentence = annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);
        List<String> listTE = new ArrayList<String>();
        // this for loop will print out all of the tokens and the character offset info
        for (CoreLabel token : firstSentence.get(CoreAnnotations.TokensAnnotation.class)) {
            listTE.add(token.word());
        }

        /* There are two possible events:
            - A verb event (we therefore look for the pattern nmod(word-\d, te-\d)
                - Sometimes with :tmod, :on, or advmod
            - An event with a passive verb [we look for neg(X, Y) ... nsubjpass(X, word-\d)
         */
        String patternNMOD = "nmod(:tmod|:on)?\\(([^-]+-\\d+), ([^-]+-\\d+)\\)"; // For NounEvents
        String patternADVMOD = "advmod(:\\w+)?\\(([^-]+-\\d+), ([^-]+-\\d+)\\)"; // For NounEvents

        String patternNounEvents21 = "nsubjpass\\(";
        String patternNounEvents22 = ", ";

        for (String tePart : listTE) {
            Pattern pText = Pattern.compile(patternNMOD);
            Matcher mText = pText.matcher(deppar);
            while (mText.find()) {
                String g1 = mText.group(2);
                String g2 = mText.group(3);
                if (g1.contains(word) & g2.contains(tePart)) { // the event is a passive-negated noun, therefore negated
                    return value;
                } else if (!g1.contains(word) & g2.contains(tePart)) { // the event is a passive-negated noun, therefore negated
                    String patText = patternNounEvents21 + word + "-\\d+" + patternNounEvents22 + g2 + "\\)";
                    Pattern pText2 = Pattern.compile(patText);
                    Matcher mText2 = pText2.matcher(deppar);
                    if (mText2.find()) {
                        return value;
                    }

                }
            }

            Pattern pText3 = Pattern.compile(patternADVMOD);
            Matcher mText3 = pText3.matcher(deppar);
            while (mText.find()) {
                String g1 = mText3.group(3);
                String g2 = mText3.group(3);
                if (g1.contains(word) & g2.contains(tePart)) { // the event is a passive-negated noun, therefore negated
                    return value;
                } else if (!g1.contains(word) & g2.contains(tePart)) { // the event is a passive-negated noun, therefore negated
                    String patText = patternNounEvents21 + word + "-\\d+" + patternNounEvents22 + g2 + "\\)";
                    Pattern pText2 = Pattern.compile(patText);
                    Matcher mText2 = pText2.matcher(deppar);
                    if (mText2.find()) {
                        return value;
                    }

                }
            }
        }
        return "";
    }

    /**
     * We check if there are any negations in the scope of the word (an event)
     *
     * @param deppar: dependency parsing string
     * @param word: the word whose scope may include negation
     * @return boolean true: if the event is negated false: if there is no
     * negation in the scope
     */
    public boolean checkNeg(String deppar, String word) {
        /* There are two possible events:
            - A negated verb event (we therefore look for the pattern neg(word-\d, X)
            - An event negated by a passive verb [we look for neg(X, Y) ... nsubjpass(X, word-\d)
         */
        String patternNounEvents1 = "neg\\(([^-]+-\\d+), ([^-]+-\\d+)\\)";
        //"nsubjpass\\("+g1+", ("+word+"-\\d+)\\)";
        String patternNounEvents21 = "nsubjpass(";
        String patternNounEvents22 = ", ";

        if (deppar.contains("neg(")) { //if there is any negation, we check
            Pattern pText = Pattern.compile(patternNounEvents1);
            Matcher mText = pText.matcher(deppar);
            while (mText.find()) {
                String g1 = mText.group(1);
//                System.out.println(patternNounEvents21 + g1 + patternNounEvents22);
                if (g1.contains(word)) { // the event is a negated verb, therefore negated
                    return true;
                } else if (deppar.contains(patternNounEvents21 + g1 + patternNounEvents22)) { // the event is a passive-negated noun, therefore negated
                    return true;
                }
            }

        }
        return false;
    }
    
    
    /**
     * We check if there are the event is subordinated (xcomp), such as in "would like to X", "decide to X"
     * We check if there are the event is a hypothesis, having WOULD as aux "In that case, Part A would X"
     *
     * @param deppar: dependency parsing string
     * @param word: the word whose scope may include possibility
     * @return boolean true: if the event didn't happen false: if it did
     */
    public boolean checkXCOMP(String deppar, String word) {
        /* If the verb is subordinated, we don't care about it */
        String patternXCOMP = "xcomp\\(([^-]+-\\d+), (" + word + "-\\d+)\\)";
        Pattern pText = Pattern.compile(patternXCOMP);
        Matcher mText = pText.matcher(deppar);
        if (mText.find()) {
            return true;
        }
        
        /* If the verb is has a would, we don't care about it */
        String patternAUX = "aux\\((would-\\d+), (" + word + "-\\d+)\\)";
        pText = Pattern.compile(patternAUX);
        mText = pText.matcher(deppar);
        if (mText.find()) {
            return true;
        }

        return false;
    }
    
    

    public String preprocessInput(String input) {
        String output = input;

        /* We check for using a capital letter for parties, e.g. "A buys a land L to B" */
 /* We check the isolated letters; we change them:
            - If they are capital and not an A,        
            - If they are capital A without being after a point,
            - If it is an A before a verb that is not after a noun */
 
        /* We check for the pattern 'personA' " */
        Pattern pText = Pattern.compile("'?[L|l]and ([A-Z])'?");
        Matcher mText = pText.matcher(output);
        StringBuffer sb = new StringBuffer(output.length());
        while (mText.find()) {
            output = output.replaceFirst(mText.group(0), "Land" + mText.group(1));
            refItem.put("Land" + mText.group(1), mText.group(1));
        }
        
        /* We check for the pattern 'personA' " */
        pText = Pattern.compile("'person[ ]?([A-Z])'");
        mText = pText.matcher(output);
        while (mText.find()) {
            output = output.replaceAll(mText.group(0), "person" + mText.group(1));
//            refItem.put("Person" + mText.group(0), mText.group(1));
        }
        
        pText = Pattern.compile("\\b([B-Z])\\b");
        mText = pText.matcher(output);
        while (mText.find()) {
            mText.appendReplacement(sb,"Part" + mText.group(1));
            refItem.put("Part" + mText.group(1), mText.group(1));
        }
        mText.appendTail(sb);
        output = sb.toString();
        
        pText = Pattern.compile(".?.?\\b(A)\\b");
        mText = pText.matcher(output);
        sb = new StringBuffer(output.length());
        boolean flagDoubtA = false;
        while (mText.find()) {
            String g1 = mText.group(0);
            if (g1.length() < 2 || g1.startsWith(".")) {
                /* The begining of a paragraph or a sentence, we should check it is not a DT */
                flagDoubtA = true;
            } else {
                /* The kind of A we are looking for */
                String aux =  g1.replaceFirst("\\bA\\b", "PartA");
                refItem.put("PartA", "A");
                mText.appendReplacement(sb,aux);
            }
        }
        mText.appendTail(sb);
        output = sb.toString();

        if (flagDoubtA) {
            /* If there was a doubtful A, we use POS for deciding */
            Annotation annotation = new Annotation(output);
            preprocessPipeline.annotate(annotation);
            String chain = "";
            String globalChain = "";
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            for (CoreMap sentence : sentences) {
                chain = sentence.toString();
                TokenSequencePattern p = TokenSequencePattern.compile("(/A/) ([ !{ tag:\"NN\"} & !{tag:\"NNP\"} & !{ tag:\"VB\" } ]+ [ { tag:\"DT\" } | { word:/\\w+[A-Z]\\b/ } ]) /.*/*");
                TokenSequenceMatcher m = p.getMatcher(sentence.get(CoreAnnotations.TokensAnnotation.class));
                if (m.find()) {
                    CoreMap aa = m.mergeGroup();
                    chain = aa.toString().replaceFirst("A", "PartA");
                    refItem.put("PartA", "A");
                }
                globalChain = globalChain + chain + " ";
            }
            output = globalChain.substring(0, globalChain.length() - 1);
        }

        /* We check for the pattern 'X_X_X' " */
        int ids = 1;
        Pattern pText1 = Pattern.compile("((\\w+)_)+(\\w+)");
        Matcher mText1 = pText1.matcher(output);
        while (mText1.find()) {
            output = output.replaceAll(mText1.group(0), "Item" + ids);
            refItem.put("Item" + ids, mText1.group(0));
            ids++;
        }
        
        /* We check for the pattern 'dd/MM/yyyy " */
        output = output.replaceAll("'?(\\d?\\d) ?/ ?(January|February|March|April|May|June|July|August|September|October|November|December) ?/ ?(\\d?\\d?\\d\\d)'?", "$1 $2 $3");
        
//        checkMinor();
        Annotation annotation = new Annotation(output);
        preprocessPipeline.annotate(annotation);
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            TokenSequencePattern p = TokenSequencePattern.compile("/because/? ([ { tag:\"NN\"} | {tag:\"NNP\"} ]) [ { lemma:\"be\" } ] [ { tag:\"DT\" } ]? [ { lemma:\"minor\" } ]");
            TokenSequenceMatcher m = p.getMatcher(sentence.get(CoreAnnotations.TokensAnnotation.class));
            while (m.find()) {
                output = output.replaceFirst(m.group(0), "");
                logical.add("minor(" + m.group(1) + ").\n");
            }
            /* We replace contract synonyms for contract */
            p = TokenSequencePattern.compile("([ { lemma:\"agreement\" } ])");
            m = p.getMatcher(sentence.get(CoreAnnotations.TokensAnnotation.class));
            while (m.find()) {
                output = output.replaceFirst(m.group(0), "contract");
            }
            p = TokenSequencePattern.compile("/because/? ([ { tag:\"NN\"} | {tag:\"NNP\"} ]) [ { lemma:\"be\" } ] [ { tag:\"DT\" } ]? ([ { ner:\"DURATION\" } ]+)");
            m = p.getMatcher(sentence.get(CoreAnnotations.TokensAnnotation.class));
            while (m.find()) {
                CoreMap age = m.mergeGroup(2);
                for (CoreLabel token : age.get(CoreAnnotations.TokensAnnotation.class)) {
                    String normalized = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
                    pText = Pattern.compile("P(\\d+)Y");
                    mText = pText.matcher(normalized);
                    if (mText.find()) {
                        int ageint = Integer.parseInt(mText.group(1));
                        if(ageint < 20){
                            logical.add("minor(" + m.group(1) + ").\n");
                        }
                        break;
                    }
                }
                output = output.replaceFirst(m.group(0), "");
            }
        }

        return output;
    }

    /* Functions of TicTag, maybe useful for further evaluation */
//    public boolean evaluateTE3() {
//        try {
//            ManagerTempEval3 mte3 = new ManagerTempEval3();
//            List<FileTempEval3> list = mte3.lista;
//            for (FileTempEval3 f : list) {
//                String input = f.getTextInput();
//                String output = annotate(input);
//                f.writeOutputFile(output);
//            }
//        } catch (Exception ex) {
//            Logger.getLogger(LegalWhen.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return false;
//    }
//
//    public boolean evaluateTE3ES() {
//        try {
//            ManagerTempEval3ES mte3 = new ManagerTempEval3ES();
//            List<FileTempEval3ES> list = mte3.lista;
//            for (FileTempEval3ES f : list) {
//                String input = f.getTextInput();
//                String output = annotate(input);
//                f.writeOutputFile(output);
//            }
//        } catch (Exception ex) {
//            Logger.getLogger(LegalWhen.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return false;
//    }
//
//    public boolean evaluateTimeBank() {
//        try {
//            ManagerTimeBank mtb = new ManagerTimeBank();
//            List<FileTimeBank> list = mtb.lista;
//            for (FileTimeBank f : list) {
//                String input = f.getTextInput();
//                String output = annotate(input);
//                f.writeOutputFile(output);
//            }
//        } catch (Exception ex) {
//            Logger.getLogger(LegalWhen.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return false;
//    }
//
    public boolean writeFile(String input, String path) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            BufferedWriter bw = new BufferedWriter(w);
            bw.write(input);
            bw.flush();
            bw.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(ContractFrames.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private String[] preprocessACoref(String mentionSpan, String output, int newMention, String current) {
        String[] out = {"", ""};
        out[0] = output;
        String toRep = mentionSpan.replaceAll("\\b(\\w+)\\b", "(?<!\\\\w)(<[^>]+>)?$1(<[^>]+>)?(?!\\\\w)");
//        String toRep = mentionSpan.replaceAll("\\b", "(<[^>]+>)?");
        StringBuffer sb = new StringBuffer(output.length());
        
        Pattern pContract = Pattern.compile(toRep);
        Matcher mText = pContract.matcher(output);
        if (mText.find()) {
            int help = mText.groupCount();
            int i = 1;
            while (mText.group(i) == null && i < help) {
                i++;
                if (i == help) {
                    if (!current.isEmpty()) {
                        mText.appendReplacement(sb,current);
                        out[1] = current;
                    }
                    else{
                        return out;
                    }
                }
            }      
            
            if(out[1].isEmpty()){
//                String aux = output.replaceFirst(mText.group(0), "\\$" + mText.group(i).replaceAll(">", "").replaceAll("<", "").replaceAll("/", "") + newMention);
                out[1] = "\\$" + mText.group(i).replaceAll(">", "").replaceAll("<", "").replaceAll("/", "") + newMention;
                mText.appendReplacement(sb,"\\$" + mText.group(i).replaceAll(">", "").replaceAll("<", "").replaceAll("/", "") + newMention);
                
            }
            
            mText.appendTail(sb);
            out[0] = sb.toString();
            
            return out;
        }

        return out;
    }

    public boolean writeLogicFile(String input2, String path) {
        String input = "";
        String initiate = "initiate(";
        String terminate = "terminate(";
//        initiate("contract"; end; date)
//        terminate("contract"; end; date)

        // For each sentence, s
        String[] output2 = input2.split("\\.");
        for (String s : output2) {
            String date = "UNKNOWN";
            String what = "";
            String type = "";
            Pattern pTE = Pattern.compile("\\$([^\\s]*)");
            Matcher mTE = pTE.matcher(s);
            while (mTE.find()) {
                String in = mTE.group(1);
                if (in.startsWith("DATE")) {
                    date = in.substring(4);
                } else if (in.equals("EVENTEND")) {
                    type = terminate;
                } else if (in.startsWith("EVENTEND")) {
                    what = in;
                } else if (in.equals("EVENTESTABLISH")) {
                    type = initiate;
                } else if (in.startsWith("EVENTESTABLISH")) {
                    what = in;
                } else {
                    what = in;
                }
            }
//            if (!date.equals("") && !what.equals("") && !type.equals("")) {
            if (!what.equals("") && !type.equals("")) {
                input = input + type + what + "," + date + ");\n";
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(path);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            BufferedWriter bw = new BufferedWriter(w);
            bw.write(input);
            bw.flush();
            bw.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(ContractFrames.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

     /**
     * We check the arguments of the purchase frame for an event 'buy'
     *
     * @param deppar: dependency parsing string
     * @param word: the event word
     * @param frameIn: the frame (that might be partially completed)
     * @return the frame with new information (or not)
     */
    public PurchaseFrame checkPurchaseBuy(String deppar, String word, final PurchaseFrame frameIn) {

        PurchaseFrame frame = frameIn;
        
        /* We check if i is a passive */
        Pattern pText1 = Pattern.compile("nsubjpass\\(" + word +  "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find()){ // passive sentence
            frame.setItem(mText1.group(1));
            pText1 = Pattern.compile("nmod:agent\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getBuyer().isEmpty()){
                // Passive sentence
                frame.setBuyer(mText1.group(1));
            }
            pText1 = Pattern.compile("nmod:from\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getSeller().isEmpty()){
                // Passive sentence
                frame.setSeller(mText1.group(1));
            }
            return frame;
        }
        
        /* We check the subject-buyer */         
        pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getBuyer().isEmpty()) {
            frame.setBuyer(mText1.group(1));
        }
        
        /* We check the from-seller */         
        pText1 = Pattern.compile("nmod:from\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getSeller().isEmpty()) {
            frame.setSeller(mText1.group(1));
        }
        
        /* We check the item, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:of\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getSeller().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getItem().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        
        /* We check the contract, that can be an nmod:by; it can also be checked later the contract mentions in the sentence */         
        pText1 = Pattern.compile("nmod:by\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getContract().isEmpty()) {
            frame.setContract(mText1.group(1));
        }
        
        /* Date and price will be done later */
        
        /* This does not look like a real frame... */
        if(frame.getBuyer().isEmpty() && frame.getSeller().isEmpty()){
            return new PurchaseFrame();
        }
        
        return frame;
    }
    
    
     /**
     * We check the arguments of the purchase frame for an establishment
     *
     * @param deppar: dependency parsing string
     * @param word: the event word
     * @param frameIn: the frame (that might be partially completed)
     * @return the frame with new information (or not)
     */
    public PurchaseFrame checkPurchaseEstablish(String deppar, String word, PurchaseFrame frameIn) {

        PurchaseFrame frame = frameIn;
        String kindOfContract = "";
        String contr = "";
        
                
        /* We check the contract, that can is the dobj( of establish; it can also be checked later the contract mentions in the sentence */             
        /* We check the type of contract */         
        Pattern pText1 = Pattern.compile("(advcl|acl):to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find()) {
            kindOfContract = mText1.group(2);
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        while(mText1.find()) {
            contr = mText1.group(1);
            if (frame.getContract().isEmpty()) {
                frame.setContract(contr);
            }
            Pattern pText2 = Pattern.compile("(advcl|acl)(:to)?\\(" + mText1.group(1) + "-\\d+, ([^-]+)-\\d+\\)");
            Matcher mText2 = pText2.matcher(deppar);
            if(mText2.find()){
                kindOfContract = mText2.group(3);
            }
        }
        
        /* We check the subject-buyer */         
        pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find()){
            if((kindOfContract.contains("b") || kindOfContract.contains("purchase")  || kindOfContract.contains("acqui")) && frame.getBuyer().isEmpty()){
                frame.setBuyer(mText1.group(1));
            }
            else if((kindOfContract.contains("s") || kindOfContract.contains("ven")) && frame.getSeller().isEmpty()){
                frame.setSeller(mText1.group(1));
            }
        }
        
        /* We check the from-seller */         
        pText1 = Pattern.compile("nmod:with\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find()){
            if((kindOfContract.contains("b") || kindOfContract.contains("purchase")  || kindOfContract.contains("acqui")) && frame.getSeller().isEmpty()){
                frame.setSeller(mText1.group(1));
            }
            else if((kindOfContract.contains("s") || kindOfContract.contains("ven")) && frame.getBuyer().isEmpty()){
                frame.setBuyer(mText1.group(1));
            }
        }
        else{
            pText1 = Pattern.compile("nmod:with\\((" + kindOfContract + "|" + contr + ")-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);
            if (mText1.find()){
                if((kindOfContract.contains("b") || kindOfContract.contains("purchase")  || kindOfContract.contains("acqui")) && frame.getSeller().isEmpty()){
                    frame.setSeller(mText1.group(2));
                }
                else if((kindOfContract.contains("s") || kindOfContract.contains("ven")) && frame.getBuyer().isEmpty()){
                    frame.setBuyer(mText1.group(2));
                }
            }
        }
        
        /* We check the item, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:of\\(" + kindOfContract + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getSeller().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + kindOfContract + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getItem().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        
//        /* We check the contract, that can be an nmod:by; it can also be checked later the contract mentions in the sentence */         
//        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
//        mText1 = pText1.matcher(deppar);
//        if (mText1.find() && frame.getContract().isEmpty()) {
//            frame.setContract(mText1.group(1));
//        }

        
        /* Date and price will be done later */
        
        return frame;
    }
    
    
    /**
     * We check the arguments of the purchase frame for an event 'sell'
     *
     * @param deppar: dependency parsing string
     * @param word: the event word
     * @param frameIn: the frame (that might be partially completed)
     * @return the frame with new information (or not)
     */
    public PurchaseFrame checkPurchaseSell(String deppar, String word, PurchaseFrame frameIn) {

        PurchaseFrame frame = frameIn;
        
        /* We check if i is a passive */
        Pattern pText1 = Pattern.compile("nsubjpass\\(" + word +  "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find()){ // passive sentence
            frame.setItem(mText1.group(1));
            pText1 = Pattern.compile("nmod:agent\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getSeller().isEmpty()){
                // Passive sentence
                frame.setSeller(mText1.group(1));
            }
            pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getBuyer().isEmpty()){
                // Passive sentence
                frame.setBuyer(mText1.group(1));
            }
            return frame;
        }
        
        /* We check the subject-seller */         
        pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getSeller().isEmpty()) {
            frame.setSeller(mText1.group(1));
        }
        
        /* We check the to-buyer */         
        pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getBuyer().isEmpty()) {
            frame.setBuyer(mText1.group(1));
        }
        
        /* We check the item, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:of\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getSeller().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getItem().isEmpty()) {
            frame.setItem(mText1.group(1));
        }
        
        /* We check the contract, that can be an nmod:by; it can also be checked later the contract mentions in the sentence */         
        pText1 = Pattern.compile("nmod:by\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getContract().isEmpty()) {
            frame.setContract(mText1.group(1));
        }
        
        /* Date and price will be done later */
        
        return frame;
    }

    /**
     * Writes a PROLEG file in a file.
     */
    private String writeProlegFile(String path) {
        String input = "";
        for(String s : logical){
            input = input + s;
        }
        for(PurchaseFrame f : framesPurchase){
            input = input + f.toProleg();
        }
        for(RescissionFrame f : framesRescission){
            f = completeRescission(framesPurchase, f);
            input = input + f.toProleg();
        }
        // We recover the items' original names
        for(String k : refItem.keySet()){
            input = input.replaceAll(k, refItem.get(k)); 
        }
        // We recover the parts' original names
        
//        input = input.replaceAll("Land([A-Z])", "$1");
//        input = input.replaceAll("Part([A-Z])", "$1");
//        input = input.replaceAll("Person([A-Z])", "$1");
        
        try {
            FileOutputStream fos = new FileOutputStream(path);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            BufferedWriter bw = new BufferedWriter(w);
            bw.write(input);
            bw.flush();
            bw.close();
        } catch (Exception ex) {
            Logger.getLogger(ContractFrames.class.getName()).log(Level.SEVERE, null, ex);
        }
        return input;
    }

    public RescissionFrame checkEnd(String deppar, String word, RescissionFrame frameIn) {
        
        RescissionFrame frame = frameIn;
        
        /* We check the subject-seller */         
        Pattern pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifester().isEmpty()) {
            frame.setManifester(mText1.group(1));
        }
        
//        /* We check the to-buyer */         
//        pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
//        mText1 = pText1.matcher(deppar);
//        if (mText1.find() && frame.getBuyer().isEmpty()) {
//            frame.setBuyer(mText1.group(1));
//        }
        
        /* We check the item, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:of\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getAction().isEmpty()) {
            frame.setAction(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getAction().isEmpty()) {
            frame.setAction(mText1.group(1));
        }
        
        /* If it is another rescission, we look for it in previous rescissions */
        if(!frame.getAction().contains("contract")){
            int len = framesRescission.size() - 1;
            for(int j=len;j>=0;j--){
                RescissionFrame f = framesRescission.get(j);
                frame.setAction(f.toResc());
                break;
            }
        }
        
        
//        /* We check the contract, that can be an nmod:by; it can also be checked later the contract mentions in the sentence */         
//        pText1 = Pattern.compile("nmod:by\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
//        mText1 = pText1.matcher(deppar);
//        if (mText1.find() && frame.getContract().isEmpty()) {
//            frame.setContract(mText1.group(1));
//        }
        
        /* Date and price will be done later */
        
        return frame;
    }
    
    public RescissionFrame checkNounEnd(String deppar, String word, RescissionFrame frameIn) {
        
        RescissionFrame frame = frameIn;
        
        /* We check the subject-seller */         
//        Pattern pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        Pattern pText1 = Pattern.compile("nsubjpass\\(([^-]+-\\d+), " + word + "-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find()){
            pText1 = Pattern.compile("nmod:agent\\(" + mText1.group(1) + ", ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getManifester().isEmpty()){
                // Passive sentence
                frame.setManifester(mText1.group(1));
            }
        }
        
//        /* We check the to-buyer */         
//        pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
//        mText1 = pText1.matcher(deppar);
//        if (mText1.find() && frame.getBuyer().isEmpty()) {
//            frame.setBuyer(mText1.group(1));
//        }
        
        /* We check the item, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:of\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getAction().isEmpty()) {
            frame.setAction(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getAction().isEmpty()) {
            frame.setAction(mText1.group(1));
        }
        
        /* Date and price will be done later */
        
        return frame;
    }
    
    
    
    public DuressFrame checkThreat(String deppar, String word, DuressFrame frameIn) {
        
        DuressFrame frame = frameIn;
        
        Pattern pText1 = Pattern.compile("nsubjpass\\(" + word +  "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find()){ // passive sentence
            frame.setManifestee(mText1.group(1));
            pText1 = Pattern.compile("nmod:agent\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);        
            if(mText1.find() && frame.getManifester().isEmpty()){
                // Passive sentence
                frame.setManifester(mText1.group(1));
            }
            return frame;
        }
        
        /* Check due to PART +ing/ed PART not being a passive*/
        if(word.endsWith("ing") || word.endsWith("ed")){
            pText1 = Pattern.compile("amod\\(([^-]+)-\\d+, " + word + "-\\d+\\)");
            mText1 = pText1.matcher(deppar);
            if (mText1.find() && frame.getManifestee().isEmpty()) {
                frame.setManifestee(mText1.group(1));
                pText1 = Pattern.compile("dep\\(([^-]+)-\\d+, " + mText1.group(1) + "-\\d+\\)");
                mText1 = pText1.matcher(deppar);
                if (mText1.find() && frame.getManifester().isEmpty()) {
                    frame.setManifester(mText1.group(1));

                }
                return frame;
            }
            pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
            mText1 = pText1.matcher(deppar);
            if (mText1.find() && frame.getManifestee().isEmpty()) {
                frame.setManifestee(mText1.group(1));
                pText1 = Pattern.compile("acl\\(([^-]+)-\\d+, " + mText1.group(1) + "-\\d+\\)");
                mText1 = pText1.matcher(deppar);
                if (mText1.find() && frame.getManifester().isEmpty()) {
                    frame.setManifester(mText1.group(1));

                }
                return frame;
            }
        }
        
        
        /* We check the subject-seller */         
        pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifester().isEmpty()) {
            frame.setManifester(mText1.group(1));
        }
        
        /* We check the threatened, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifestee().isEmpty()) {
            frame.setManifestee(mText1.group(1));
        }
        pText1 = Pattern.compile("dobj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifestee().isEmpty()) {
            frame.setManifestee(mText1.group(1));
        }
        
        return frame;
    }

    
    public DuressFrame checkDuress(String deppar, String word, DuressFrame frameIn) {
        
        DuressFrame frame = frameIn;
        
        /* We check the possesor-doer */         
        Pattern pText1 = Pattern.compile("nmod:poss\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        Matcher mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifester().isEmpty()) {
            frame.setManifester(mText1.group(1));
        }
        pText1 = Pattern.compile("nmod:of\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifester().isEmpty()) {
            frame.setManifester(mText1.group(1));
        }
        
//        /* We check the to-buyer */         
//        pText1 = Pattern.compile("nmod:to\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
//        mText1 = pText1.matcher(deppar);
//        if (mText1.find() && frame.getBuyer().isEmpty()) {
//            frame.setBuyer(mText1.group(1));
//        }
        
        /* We check the threatened, that can be an nmod:of or a dobj */         
        pText1 = Pattern.compile("nsubj\\(" + word + "-\\d+, ([^-]+)-\\d+\\)");
        mText1 = pText1.matcher(deppar);
        if (mText1.find() && frame.getManifestee().isEmpty()) {
            frame.setManifestee(mText1.group(1));
        }
        
        return frame;
    }

    

    private RescissionFrame completeRescission(List<PurchaseFrame> framesPurchase, RescissionFrame in) {
        int len = framesPurchase.size() - 1;
        for (int j = len ; j>=0; j--) {
            PurchaseFrame f = framesPurchase.get(j);
            if (in.getAction().contains(f.getContract())) {
                if (f.getBuyer().equals(in.getManifester())) {
                    in.setManifestee(f.getSeller());
                } else if (f.getSeller().equals(in.getManifester())) {
                    in.setManifestee(f.getBuyer());
                } else if (f.getSeller().equals(in.getManifestee())) {
                    in.setManifester(f.getBuyer());
                } else if (f.getBuyer().equals(in.getManifestee())) {
                    in.setManifester(f.getSeller());
                }
                break;
            }
        }
        DuressFrame dur = in.getDuress();
        if (!dur.isEmpty()) {
            if (dur.getManifester().isEmpty() || isApronoun(dur.getManifester())) {
                if (in.getManifestee().equals(dur.getManifestee())) {
                    dur.setManifester(in.getManifester());
                } else {
                    dur.setManifester(in.getManifestee());
                }
            }
            if (dur.getManifestee().isEmpty() || isApronoun(dur.getManifestee())) {
                if (in.getManifester().equals(dur.getManifester())) {
                    dur.setManifestee(in.getManifester());
                } else {
                    dur.setManifestee(in.getManifester());
                }
            }
        }
        
        return in;
    }

    private void cleanGarb() {
        refItem = new HashMap<String,String>();
        logical = new ArrayList<String>();
        framesPurchase = new ArrayList<PurchaseFrame>();
        framesRescission = new ArrayList<RescissionFrame>();
        framesDuress = new ArrayList<DuressFrame>();
    }
    
    private boolean isApronoun(String in){
        if(in.matches("\\b(him|her|he|she|they|them)\\b")){
            return true;
        }
        return false;
    }

}
