namespace Pixous.HrPortal.Domain.Common;

/// <summary>
/// The single exception type services throw, carrying an <see cref="ErrorCode"/>
/// that the exception middleware turns into a status.
///
/// <para>The three factories match the Java ones exactly, message text
/// included: <c>notFound("User")</c> produces "User not found", and that string
/// reaches the user, so changing it changes what somebody reads on screen.</para>
/// </summary>
public sealed class ApiException : Exception
{
    public ErrorCode Code { get; }

    public ApiException(ErrorCode code, string message) : base(message) => Code = code;

    public static ApiException NotFound(string what) =>
        new(ErrorCode.NotFound, what + " not found");

    public static ApiException Conflict(string message) =>
        new(ErrorCode.Conflict, message);

    public static ApiException Business(string message) =>
        new(ErrorCode.BusinessRule, message);
}
