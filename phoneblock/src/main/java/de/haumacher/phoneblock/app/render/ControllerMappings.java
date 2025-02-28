package de.haumacher.phoneblock.app.render;

import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.web.IWebRequest;

public class ControllerMappings {

    private static Map<String, WebController> controllersByURL;


    static {
        controllersByURL = new HashMap<String, WebController>();
        controllersByURL.put("/", new DefaultController());
    }
    
    public static WebController resolveControllerForRequest(final IWebRequest request) {
        final String path = getRequestPath(request);
        return controllersByURL.getOrDefault(path, DefaultController.INSTANCE);
    }


    // Path within application might contain the ";jsessionid" fragment due to URL rewriting
    private static String getRequestPath(final IWebRequest request) {
        String requestPath = request.getPathWithinApplication();

        final int fragmentIndex = requestPath.indexOf(';');
        if (fragmentIndex != -1) {
            requestPath = requestPath.substring(0, fragmentIndex);
        }

        return requestPath;
    }


    private ControllerMappings() {
        super();
    }

}
