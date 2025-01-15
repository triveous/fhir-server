package ca.uhn.fhir.jpa.starter.authnz.inbound.authorization.enforcer;

import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilder;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleFinished;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.IdType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface EnforcerUtils {
    static <T> IAuthRuleBuilder chain(IAuthRuleBuilder builder, Collection<T> items, Function<T, IAuthRuleFinished> fun) {
        var localBuilder = builder;
        for (T data : items) {
            var ruleFinished = fun.apply(data);
            localBuilder = ruleFinished != null ? ruleFinished.andThen() : localBuilder;
        }
        return localBuilder;
    }

    /**
     * Extract the resource Id
     * @param argument List of string with each have pattern representing resource identifier <ResourceType>/<ResourceId>
     *                 Additionally multiple id can be provided comma separated
     *                 Ex: <ResourceType>/<ResourceId>,<ResourceType>/<ResourceId>,<ResourceType>/<ResourceId>
     * @return List of {@link IdType} extracted from all argument
     */
    static List<IdType> extractIds(Collection<String> argument) {
        var pattern = Pattern.compile("^(.+?)/(.+?)$");
        return argument.stream()
                .flatMap(a-> Arrays.stream(a.split(",")))
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> new IdType(matcher.group(1).trim(), matcher.group(2).trim()))
                .collect(Collectors.toList());
    }

    /**
     * Extract the resource and the operation
     * @param argument List of string with each have pattern representing resource identifier <ResourceType>/<OperationName>
     *                 Additionally multiple op can be provided comma separated
     *                 Ex:  <ResourceType>/<OperationName>, <ResourceType>/<OperationName>, <ResourceType>/<OperationName>
     * @return List of Pair where left is the resource name and the right is the Operation Name
     */
    static List<Pair<String, String>> extractResourceAndOp(Collection<String> argument) {
        var pattern = Pattern.compile("^(.+?)/(.+?)$");
        return argument.stream()
                .flatMap(a-> Arrays.stream(a.split(",")))
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Pair.of(matcher.group(1).trim(), matcher.group(2).trim()))
                .collect(Collectors.toList());
    }

    /**
     * Extract the resource
     * @param argument List of string with each have pattern representing resource
     *                 Additionally multiple op can be provided comma separated
     *                 Ex:  <ResourceType>,<ResourceType>,<ResourceType>
     * @return List of Pair where left is the resource name and the right is the Operation Name
     */
    static List<String> extractResourceType(Collection<String> argument) {
        return argument.stream()
                .flatMap(a-> Arrays.stream(a.split(",")))
                .map(String::trim)
                .collect(Collectors.toList());
    }


    /**
     * Extract the Resource1 and the Resource2Id
     * @param argument List of string with each have pattern representing resource identifier <ResourceType>:<ResourceType>/<ResourceId>
     *                 Additionally multiple op can be provided comma separated
     *                 Ex: <ResourceType>:<ResourceType>/<ResourceId>,<ResourceType>:<ResourceType>/<ResourceId>
     * @return List of Pair where left is the resource name and the right is the IdType
     */
    static List<Pair<String, IdType>> extractResourceAndIds(Collection<String> argument) {
        var pattern = Pattern.compile("^([^:]+):([^/]+)/(.+)$");
        return argument.stream()
                .flatMap(a-> Arrays.stream(a.split(",")))
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Pair.of(matcher.group(1).trim(), new IdType(matcher.group(2).trim(), matcher.group(3).trim())))
                .collect(Collectors.toList());
    }
}
