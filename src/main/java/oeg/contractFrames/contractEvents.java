package oeg.contractFrames;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;

import java.util.List;

/**
 * Example annotations
 *
 * @author Angel Chang
 */
public class contractEvents {
    
   private contractEvents() { }
  // My custom tokens
  public static class ContractEventTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.<Class<String>> uncheckedCast(String.class);
    }
  }

  // My custom type
  public static class DateTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.<Class<String>> uncheckedCast(String.class);
    }
  }

  // My custom value
  public static class MyValueAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.<Class<String>> uncheckedCast(String.class);
    }
  }
}
