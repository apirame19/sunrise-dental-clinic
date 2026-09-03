package lk.sunrisedental.controller.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

/**
 * Forces UTF-8 on every request and response.
 *
 * <p>This must be the first filter in the chain. {@code setCharacterEncoding} has no effect once
 * any parameter has been read - the container has already decoded the body by then, using the
 * platform default. On a Windows machine that default is Windows-1252, which turns every Sinhala or
 * Tamil patient name into mojibake at the moment it is submitted, and no later correction can
 * recover the original bytes.</p>
 *
 * <p>The encoding is set unconditionally rather than only when absent. Browsers routinely omit the
 * charset from a form post, so "only if not already set" would mean "almost never set".</p>
 */
public class EncodingFilter implements Filter {

    private static final String UTF_8 = "UTF-8";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        request.setCharacterEncoding(UTF_8);
        response.setCharacterEncoding(UTF_8);
        chain.doFilter(request, response);
    }
}
