
package oeg.contractFrames;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.fusesource.jansi.AnsiConsole;

/**
 * InspecteeError collector filtra todos los mensajes de log. 
 * Y da mensaje de error visualmente como respuesta a los mensaje:
 * "POPUP <mensaje>"
 * "TIMED <mensaje>"
 * @author vrodriguez
 */

public class ErrorCollector extends AppenderSkeleton {
    private static ArrayList<LoggingEvent> events = new ArrayList();
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_RED_BRIGHT = "\u001B[31;1m";
    public static final String ANSI_RED2 = "\u001B[41m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_BLUE_BRIGHT = "\u001B[34;1m";

    private static boolean iniciado = false;
    public static void init() {
        AnsiConsole.systemInstall();
        iniciado = true;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                AnsiConsole.systemInstall();
            }
        }));
    }

    @Override
    protected void append(LoggingEvent event) {
        if (!iniciado)
            init();
        
        String slevel = event.getLevel().toString();
        if (slevel.equals("ERROR"))
        {
  //          System.err.println(event.getMessage());
            events.add(event);
            Object o = event.getMessage();
            if (o.getClass().equals(String.class))
            {
                String s = (String)o;
                if (s.startsWith("POPUP "))
                {
                    s = s.replace("POPUP ", "");
                    JOptionPane.showMessageDialog(null, s, "Info", JOptionPane.ERROR_MESSAGE);
                }
            }
            System.err.println(ANSI_RED_BRIGHT + "[WARN]" + ANSI_RESET + " " + event.getMessage());
            
        }
        if (slevel.equals("INFO"))
        {
            Object o = event.getMessage();
            if (o.getClass().equals(String.class))
            {
                String s = (String)o;
                if (s.startsWith("POPUP "))
                {
                    s = s.replace("POPUP ", "");
                    JOptionPane.showMessageDialog(null, s, "Info", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
  //      if (Main.logs)

        if (slevel.equals("WARN"))
        {
            String texto = event.getRenderedMessage();
            if (texto==null)
            {
                System.err.println("Warning nulo en ErrorCollector");
                return;
            }
            else
            {
                String str = "Warning desconocido en ErrorCollector ";
                if (event!=null && event.getMessage()!=null)
                    str+=event.getMessage();
         //       System.err.println(str);
            }
            if (Main.logs)
                System.out.println(ANSI_RED + "[WARN]" + ANSI_RESET + " " + event.getMessage());
        }

    }

    public void close() {
    }

    public boolean requiresLayout() {
        return false;
    }
    
    public static String getErrorStatus()
    {
        String mensaje="ok";
        if(events.isEmpty())
            return "ok";
        for(LoggingEvent event : events)
        {
            if (event.getRenderedMessage().contains("nodata"))
                return "nodata";
            if (event.getRenderedMessage().contains("nochannel"))
                return "nochannel";
        }
        return "error";
    }
    
    
}