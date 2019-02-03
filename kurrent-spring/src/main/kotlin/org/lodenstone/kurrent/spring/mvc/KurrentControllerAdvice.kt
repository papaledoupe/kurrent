package org.lodenstone.kurrent.spring.mvc

import org.lodenstone.kurrent.core.aggregate.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import javax.servlet.http.HttpServletResponse

@RestControllerAdvice
class KurrentControllerAdvice : ResponseEntityExceptionHandler() {

    @ExceptionHandler
    fun handle(exception: AggregateVersionConflictException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.CONFLICT.value())
    }

    @ExceptionHandler
    fun handle(exception: AggregateIdConflictException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.CONFLICT.value())
    }

    @ExceptionHandler
    fun handle(exception: NoSuchAggregateTypeException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.NOT_FOUND.value())
    }

    @ExceptionHandler
    fun handle(exception: NoSuchAggregateException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.NOT_FOUND.value())
    }

    @ExceptionHandler
    fun handle(exception: NoSuchCommandException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.BAD_REQUEST.value())
    }

    @ExceptionHandler
    fun handle(exception: NoSuchEventException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value())
    }

    @ExceptionHandler
    fun handle(exception: RejectedCommandException, httpServletResponse: HttpServletResponse) {
        httpServletResponse.sendError(HttpStatus.BAD_REQUEST.value())
    }
}