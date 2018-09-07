package oeg.contractFrames;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 * Class to run experiments and test the software. 
 * @author Maria
 */
public class testDataset {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String inputFolder = "../ContractFrames/data/train/";
        String testFolder = "../ContractFrames/data/solv/proleg";
        String outputFolder = "../ContractFrames/data/outp/";

        long startTime = System.currentTimeMillis();
        ContractFrames cf = new ContractFrames();
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;

        startTime = System.currentTimeMillis();

        File inputF = new File(inputFolder);
        for (File f : inputF.listFiles()) {
            try {
                String txt = FileUtils.readFileToString(f, "UTF-8");

                System.out.println("\n\n--------------------\nSENTENCE " + cf.numindex + "\n--------------------\n");
                System.out.println(txt);
                System.out.println("\n--------------------\nOUTPUT\n--------------------\n\n");
                String output = cf.annotate(txt);
                writeFile(output, outputFolder + f.getName());
                System.out.println(output);

            } catch (IOException ex) {
                Logger.getLogger(testDataset.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        stopTime = System.currentTimeMillis();

        System.out.println("-------------------------------------------");
        System.out.println("Loading time: " + elapsedTime / 1000 + "s");
        System.out.println("Avg annotation time: " + (stopTime - startTime) / 2000 + "s");
        System.out.println("-------------------------------------------");
        
        String output = "";
        File outF = new File(outputFolder);
        for (File f : outF.listFiles()) {
            try {
                String route = testFolder + f.getName();
                String s1 = FileUtils.readFileToString(f, "UTF-8");
//                System.out.println(s1);
                String s2 = FileUtils.readFileToString(new File(route), "UTF-8");
//                System.out.println(s2);
                if(s1.equals(s2)){
                    output = output + f.getName() + "\t CORRECT\n";
                }
                else{
                    output = output + f.getName() + "\t FAIL\n";
                }
            } catch (IOException ex) {
                Logger.getLogger(testDataset.class.getName()).log(Level.SEVERE, null, ex);
            }
                
        }
        
        System.out.println("-------------------------------------------");
        System.out.println("---- RESULT");
        System.out.println("-------------------------------------------");
        
        System.out.println(output);
        
        writeFile(output, "result.txt");

    }

    static public boolean writeFile(String input, String path) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            BufferedWriter bw = new BufferedWriter(w);
            bw.write(input);
            bw.flush();
            bw.close();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(testDataset.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

}
