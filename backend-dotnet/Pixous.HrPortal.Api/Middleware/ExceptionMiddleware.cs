using System.Text.Json;
using Pixous.HrPortal.Domain.Common;

namespace Pixous.HrPortal.Api.Middleware;

/// <summary>
/// Turns an exception into the same envelope and the same status the Java
/// <c>GlobalExceptionHandler</c> produced.
///
/// <para>Middleware rather than an exception filter, because a filter only sees
/// what reaches MVC: a failure in authentication or in the CORS handler would
/// escape it and come back as an empty 500 with no envelope, which the client
/// renders as "Network Error". The Java equivalent was a
/// <c>@RestControllerAdvice</c> plus an entry-point handler doing the same job
/// in two places.</para>
/// </summary>
public sealed class ExceptionMiddleware(RequestDelegate next, ILogger<ExceptionMiddleware> log)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (ApiException ex)
        {
            // An expected failure: a business rule, a missing row, a conflict.
            // Logged at information because it is the application working, not
            // breaking -- the Java handler did the same.
            log.LogInformation("{Code} on {Path}: {Message}",
                ex.Code, context.Request.Path, ex.Message);
            await WriteAsync(context, ex.Code, ex.Message);
        }
        catch (Exception ex)
        {
            /*
             * Anything else. The message is deliberately not passed to the
             * client: an unhandled exception's text can name a table, a column
             * or a file path, and the Java handler was careful about this too.
             * The detail goes to the log, where it belongs.
             */
            log.LogError(ex, "Unhandled error on {Path}", context.Request.Path);
            await WriteAsync(context, ErrorCode.Internal, "Something went wrong. Please try again.");
        }
    }

    private static async Task WriteAsync(HttpContext context, ErrorCode code, string message)
    {
        // A response already on its way cannot be replaced -- writing here would
        // corrupt it. Rare, and worth failing quietly rather than compounding.
        if (context.Response.HasStarted) return;

        context.Response.Clear();
        context.Response.StatusCode = (int)code.Status();
        context.Response.ContentType = "application/json";

        var body = ApiResponse<object>.Fail(message, null);
        await context.Response.WriteAsync(JsonSerializer.Serialize(body, JsonOpts));
    }

    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
    };
}
