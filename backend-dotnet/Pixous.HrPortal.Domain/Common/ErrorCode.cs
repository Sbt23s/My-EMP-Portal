using System.Net;

namespace Pixous.HrPortal.Domain.Common;

/// <summary>
/// The application's error codes and the HTTP status each maps to.
///
/// <para>Ported one-for-one from the Java enum, including the two that share
/// 422 — GEOFENCE_VIOLATION and BUSINESS_RULE. The client does not read the
/// code, but it does branch on the status, so the mapping is the contract.</para>
/// </summary>
public enum ErrorCode
{
    ValidationError,
    BadCredentials,
    Unauthenticated,
    TokenExpired,
    AccountLocked,
    AccessDenied,
    NotFound,
    Conflict,
    TooManyAttempts,
    GeofenceViolation,
    BusinessRule,
    Internal
}

public static class ErrorCodeStatus
{
    public static HttpStatusCode Status(this ErrorCode code) => code switch
    {
        ErrorCode.ValidationError => HttpStatusCode.BadRequest,           // 400
        ErrorCode.BadCredentials => HttpStatusCode.Unauthorized,          // 401
        ErrorCode.Unauthenticated => HttpStatusCode.Unauthorized,         // 401
        ErrorCode.TokenExpired => HttpStatusCode.Unauthorized,            // 401
        ErrorCode.AccountLocked => HttpStatusCode.Locked,                 // 423
        ErrorCode.AccessDenied => HttpStatusCode.Forbidden,               // 403
        ErrorCode.NotFound => HttpStatusCode.NotFound,                    // 404
        ErrorCode.Conflict => HttpStatusCode.Conflict,                    // 409
        ErrorCode.TooManyAttempts => HttpStatusCode.TooManyRequests,      // 429
        ErrorCode.GeofenceViolation => HttpStatusCode.UnprocessableEntity,// 422
        ErrorCode.BusinessRule => HttpStatusCode.UnprocessableEntity,     // 422
        _ => HttpStatusCode.InternalServerError                           // 500
    };
}
