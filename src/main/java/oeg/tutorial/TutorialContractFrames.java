/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package oeg.tutorial;

import oeg.contractFrames.ContractFrames;


/**
 *
 * @author mnavas
 */
public class TutorialContractFrames {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        // Input text goes in this variable txt
        String txt = "A established a purchase contract with B to buy a land L, on April 1, 2018. A rescinded the purchase contract by a fraud by the thrid party C on May 1, 2018. However, the recission was actually made due to B's duress on April 15, 2018 since B would like to send the land to D because D said that D pays more to purched the land than A pays. A made a rescission of the above rescission on June 1, 2018.";
        
        ContractFrames cf = new ContractFrames();
        String output = cf.annotate(txt);
        System.out.println("--------------------\nOUTPUT\n--------------------\n\n");
        
        System.out.println(output);
        
        
        
    }
    
}
