package com.jimu.http.support;

import org.springframework.context.ApplicationContext;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lazily resolves beans by name to avoid instantiating all beans on each script execution.
 */
public class LazyBeanMap extends AbstractMap<String, Object> {

    private final ApplicationContext applicationContext;
    private final Set<String> beanNames;

    public LazyBeanMap(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.beanNames = new TreeSet<>(Arrays.asList(applicationContext.getBeanDefinitionNames()));
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String beanName)) {
            return null;
        }
        if (!applicationContext.containsBean(beanName)) {
            return null;
        }
        return applicationContext.getBean(beanName);
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String beanName)) {
            return false;
        }
        return beanNames.contains(beanName);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<String, Object>> iterator() {
                Iterator<String> iterator = beanNames.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<String, Object> next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        String beanName = iterator.next();
                        return new SimpleEntry<>(beanName, applicationContext.getBean(beanName));
                    }
                };
            }

            @Override
            public int size() {
                return beanNames.size();
            }
        };
    }

    @Override
    public int size() {
        return beanNames.size();
    }
}
