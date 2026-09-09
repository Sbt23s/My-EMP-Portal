using System.Text.Json.Serialization;

namespace Pixous.HrPortal.Domain.Common;

/// <summary>
/// The envelope every REST endpoint returns, byte-for-byte as the Java one did.
/// </summary>
/// <remarks>
/// <para>The React client reads <c>res.data.data</c> everywhere and checks
/// <c>success</c> on failure paths, so the shape is a contract rather than a
/// convention. Field order matters too: the frontend does not depend on it, but
/// a diff of two responses during migration is unreadable if the keys move.</para>
///
/// <para>Nulls are omitted, matching Jackson's <c>NON_NULL</c> on the record.
/// A success response therefore carries no <c>errors</c> key at all rather than
/// <c>"errors": null</c> — the client distinguishes the two in a couple of
/// places, and reproducing the absence is cheaper than finding them.</para>
///
/// <para><c>timestamp</c> is UTC ISO-8601, as <c>Instant</c> serialised.</para>
/// </remarks>
public sealed record ApiResponse<T>(
    [property: JsonPropertyName("success")] bool Success,
    [property: JsonPropertyName("message")] string? Message,
    [property: JsonPropertyName("data")] T? Data,
    [property: JsonPropertyName("errors")] object? Errors,
    [property: JsonPropertyName("timestamp")] DateTimeOffset Timestamp)
{
    public static ApiResponse<T> Ok(T? data) =>
        new(true, "OK", data, null, DateTimeOffset.UtcNow);

    public static ApiResponse<T> Ok(T? data, string message) =>
        new(true, message, data, null, DateTimeOffset.UtcNow);

    /// <summary>
    /// Success with a message and no payload — Java's <c>ApiResponse.message</c>.
    ///
    /// <para>Named <c>Ok</c> rather than <c>Message</c> because the record
    /// already has a <c>Message</c> property and C# will not allow both. The
    /// wire shape is identical; only the C# call site differs.</para>
    /// </summary>
    public static ApiResponse<T> OkMessage(string message) =>
        new(true, message, default, null, DateTimeOffset.UtcNow);

    public static ApiResponse<T> Fail(string message, object? errors) =>
        new(false, message, default, errors, DateTimeOffset.UtcNow);
}

/// <summary>
/// The same envelope where the payload type is not interesting — the Java side
/// writes <c>ApiResponse&lt;Void&gt;</c> for these.
/// </summary>
public static class ApiResponse
{
    public static ApiResponse<object> Ok() => ApiResponse<object>.Ok(null);
    public static ApiResponse<object> Message(string message) => ApiResponse<object>.OkMessage(message);
}
