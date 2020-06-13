package com.google.cloud.healthcare.fdamystudies.util;

import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import com.google.cloud.healthcare.fdamystudies.bean.ResponseBean;
import com.google.cloud.healthcare.fdamystudies.exceptions.InvalidEmailCodeException;
import com.google.cloud.healthcare.fdamystudies.exceptions.InvalidRequestException;
import com.google.cloud.healthcare.fdamystudies.exceptions.InvalidUserIdException;
import com.google.cloud.healthcare.fdamystudies.exceptions.SystemException;
import com.google.cloud.healthcare.fdamystudies.util.MyStudiesUserRegUtil.ErrorCodes;

public final class ResponseUtil {

  private ResponseUtil() {}

  private static final Logger LOG = LoggerFactory.getLogger(ResponseUtil.class);

  public static ResponseBean prepareBadRequestResponse(
      HttpServletResponse response, String errorType) {
    return prepareBadRequestResponse(response, null, errorType);
  }

  public static ResponseBean prepareBadRequestResponse(
      HttpServletResponse response, Exception e, String... errorTypes) {
    String errorType = e != null ? mapExceptionToErrorType(e) : errorTypes[0];
    ResponseBean responseBean = new ResponseBean();
    // Default error code for missing required parameter and InvalidRequestException
    ErrorCodes errorMsg = MyStudiesUserRegUtil.ErrorCodes.INVALID_INPUT_ERROR_MSG;

    switch (errorType) {
      case AppConstants.INVALID_EMAIL_CODE_EXCEPTION:
        errorMsg = MyStudiesUserRegUtil.ErrorCodes.INVALID_EMAIL_CODE;
        break;
      case AppConstants.INVALID_USERID_EXCEPTION:
        errorMsg = MyStudiesUserRegUtil.ErrorCodes.INVALID_USER_ID;
        break;
      case AppConstants.EMAIL_NOT_EXISTS:
        errorMsg = MyStudiesUserRegUtil.ErrorCodes.EMAIL_NOT_EXISTS;
        break;
      case AppConstants.MISSING_REQUIRED_PARAMETER:
        errorMsg = MyStudiesUserRegUtil.ErrorCodes.INVALID_INPUT_ERROR_MSG;
        break;
    }

    MyStudiesUserRegUtil.getFailureResponse(
        HttpStatus.BAD_REQUEST.toString(),
        MyStudiesUserRegUtil.ErrorCodes.INVALID_INPUT.getValue(),
        errorMsg.getValue(),
        response);
    responseBean.setCode(HttpStatus.BAD_REQUEST.value());
    responseBean.setMessage(errorMsg.getValue());
    return responseBean;
  }

  public static ResponseBean prepareSystemExceptionResponse(HttpServletResponse response) {
    MyStudiesUserRegUtil.getFailureResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.toString(),
        MyStudiesUserRegUtil.ErrorCodes.UNKNOWN.getValue(),
        MyStudiesUserRegUtil.ErrorCodes.CONNECTION_ERROR_MSG.getValue(),
        response);
    ResponseBean responseBean = new ResponseBean();
    responseBean.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    responseBean.setMessage(MyStudiesUserRegUtil.ErrorCodes.CONNECTION_ERROR_MSG.getValue());
    return responseBean;
  }

  public static ResponseBean prepareSuccessResponse(HttpServletResponse response) {
    MyStudiesUserRegUtil.getFailureResponse(
        HttpStatus.OK.toString(),
        MyStudiesUserRegUtil.ErrorCodes.SUCCESS.getValue(),
        MyStudiesUserRegUtil.ErrorCodes.SUCCESS.getValue(),
        response);
    ResponseBean responseBean = new ResponseBean();
    responseBean.setCode(HttpStatus.OK.value());
    responseBean.setMessage(ErrorCode.EC_200.errorMessage());
    return responseBean;
  }

  private static String mapExceptionToErrorType(Exception e) {
    if (e instanceof InvalidRequestException) {
      return AppConstants.INVALID_REQUEST_EXCEPTION;
    } else if (e instanceof InvalidEmailCodeException) {
      return AppConstants.INVALID_EMAIL_CODE_EXCEPTION;
    } else if (e instanceof InvalidUserIdException) {
      return AppConstants.INVALID_USERID_EXCEPTION;
    } else if (e instanceof SystemException) {
      return AppConstants.SYSTEM_EXCEPTION;
    }
    return AppConstants.MISSING_REQUIRED_PARAMETER;
  }
}
