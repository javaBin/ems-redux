package no.java.ems.wiki;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author <a href="mailto:trygvis@java.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class LoggingWikiSink {
    public static <T extends WikiSink> T wrap(T sink) {
        return (T) Proxy.newProxyInstance(WikiSink.class.getClassLoader(),
            new Class<?>[]{WikiSink.class}, new LoggingInvocationHandler(sink));
    }

    private static class LoggingInvocationHandler<T extends WikiSink> implements InvocationHandler {
        private T target;

        public LoggingInvocationHandler(T target) {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == WikiSink.class) {
                System.out.println(method.getName());
            }
            return method.invoke(target, args);
        }
    }
}
