package com.numb2.writeup_editor_backend.response;

public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;

    public static ApiResponse success(String message, Object data) {
        ApiResponse response = new ApiResponse();
        response.success = true;
        response.message = message;
        response.data = data;
        return response;
    }

    public static ApiResponse error(String message) {
        ApiResponse response = new ApiResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
