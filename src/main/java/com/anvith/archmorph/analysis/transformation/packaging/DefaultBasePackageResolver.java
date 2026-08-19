package com.anvith.archmorph.analysis.transformation.packaging;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Iterator;

@Service
public class DefaultBasePackageResolver
        implements BasePackageResolver {

    /**
     * Determines the common root package
     * shared by all Java packages.
     *
     * Example:
     *
     * com.pmj.template.controller
     * com.pmj.template.service
     * com.pmj.template.repository
     * com.pmj.template.dto.request
     *
     * Result:
     *
     * com.pmj.template
     */
    @Override
    public String resolve(
            Collection<String> packages) {

        if (packages == null || packages.isEmpty()) {
            return "";
        }

        Iterator<String> iterator =
                packages.iterator();

        String common =
                iterator.next();

        while (iterator.hasNext()) {

            common = commonPrefix(
                    common,
                    iterator.next()
            );

            if (common.isBlank()) {
                break;
            }

        }

        return common;
    }

    /**
     * Computes the common package prefix
     * between two package names.
     */
    private String commonPrefix(

            String first,

            String second) {

        if (first == null || second == null) {
            return "";
        }

        String[] firstParts =
                first.split("\\.");

        String[] secondParts =
                second.split("\\.");

        int length =
                Math.min(
                        firstParts.length,
                        secondParts.length
                );

        StringBuilder builder =
                new StringBuilder();

        for (int i = 0; i < length; i++) {

            if (!firstParts[i].equals(secondParts[i])) {
                break;
            }

            if (!builder.isEmpty()) {
                builder.append(".");
            }

            builder.append(firstParts[i]);

        }

        return builder.toString();
    }

}