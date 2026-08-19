package com.anvith.archmorph.common.exception;
import com.anvith.archmorph.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidZipException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidZip(InvalidZipException ex) {

        return ResponseEntity.badRequest().body(
                ApiResponse.builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(ProjectExtractionException.class)
    public ResponseEntity<ApiResponse<?>> handleExtraction(ProjectExtractionException ex) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(ex.getMessage())
                                .build()
                );
    }

    @ExceptionHandler(WorkspaceCreationException.class)
public ResponseEntity<ApiResponse<?>> handleWorkspaceCreationException(
        WorkspaceCreationException ex) {

    return ResponseEntity.internalServerError().body(
            ApiResponse.builder()
                    .success(false)
                    .message(ex.getMessage())
                    .data(null)
                    .build()
    );
}


@ExceptionHandler(ArchiveStorageException.class)
public ResponseEntity<ApiResponse<?>> handleArchiveStorageException(
        ArchiveStorageException ex) {

    return ResponseEntity.internalServerError()
            .body(
                    ApiResponse.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .data(null)
                            .build()
            );
}

@ExceptionHandler(InvalidProjectStructureException.class)
public ResponseEntity<ApiResponse<?>> handleInvalidProjectStructure(
        InvalidProjectStructureException ex) {

    return ResponseEntity.badRequest()
            .body(
                    ApiResponse.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .data(null)
                            .build()
            );
}

@ExceptionHandler(SourceCodeNotFoundException.class)
public ResponseEntity<ApiResponse<?>> handleSourceCodeNotFound(
        SourceCodeNotFoundException ex) {

    return ResponseEntity.badRequest()
            .body(
                    ApiResponse.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .data(null)
                            .build()
            );
}

@ExceptionHandler(JavaParsingException.class)
public ResponseEntity<ApiResponse<?>> handleJavaParsing(
        JavaParsingException ex) {

    return ResponseEntity.badRequest()
            .body(
                    ApiResponse.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .data(null)
                            .build()
            );

}

    

}