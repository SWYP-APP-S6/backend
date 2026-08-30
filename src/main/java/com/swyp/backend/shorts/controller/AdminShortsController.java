package com.swyp.backend.shorts.controller;

import tools.jackson.databind.JsonNode;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.shorts.dto.ChunkCreateRequest;
import com.swyp.backend.shorts.dto.ClipReviewRequest;
import com.swyp.backend.shorts.dto.MediaRegisterRequest;
import com.swyp.backend.shorts.dto.RankRequest;
import com.swyp.backend.shorts.dto.SourceCreateRequest;
import com.swyp.backend.shorts.dto.SourceFromUrlRequest;
import com.swyp.backend.shorts.dto.SttRequest;
import com.swyp.backend.shorts.service.ShortsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/shorts")
public class AdminShortsController {

	private final ShortsService shortsService;

	@GetMapping("/health")
	public ApiResponse<JsonNode> health() {
		return ApiResponse.of(SuccessCode.OK, shortsService.health());
	}

	@GetMapping("/sources")
	public ApiResponse<JsonNode> listSources() {
		return ApiResponse.of(SuccessCode.OK, shortsService.listSources());
	}

	@GetMapping("/sources/{sourceId}")
	public ApiResponse<JsonNode> getSource(@PathVariable long sourceId) {
		return ApiResponse.of(SuccessCode.OK, shortsService.getSource(sourceId));
	}

	@GetMapping("/chunks/{chunkId}/utterances")
	public ApiResponse<JsonNode> getUtterances(@PathVariable long chunkId) {
		return ApiResponse.of(SuccessCode.OK, shortsService.getUtterances(chunkId));
	}

	@GetMapping("/jobs")
	public ApiResponse<JsonNode> listJobs() {
		return ApiResponse.of(SuccessCode.OK, shortsService.listJobs());
	}

	@GetMapping("/jobs/{jobId}")
	public ApiResponse<JsonNode> getJob(@PathVariable String jobId) {
		return ApiResponse.of(SuccessCode.OK, shortsService.getJob(jobId));
	}

	@PostMapping("/sources")
	public ResponseEntity<ApiResponse<JsonNode>> createSource(
			@AuthenticationPrincipal Long adminId, @Valid @RequestBody SourceCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(SuccessCode.CREATED, shortsService.createSource(request, adminId)));
	}

	@GetMapping("/media")
	public ApiResponse<JsonNode> listMedia() {
		return ApiResponse.of(SuccessCode.OK, shortsService.listMedia());
	}

	@DeleteMapping("/media/{name}")
	public ApiResponse<JsonNode> deleteMedia(@PathVariable String name) {
		return ApiResponse.of(SuccessCode.OK, shortsService.deleteMedia(name));
	}

	@PostMapping("/media/{name}/register")
	public ApiResponse<JsonNode> registerMedia(
			@AuthenticationPrincipal Long adminId,
			@PathVariable String name,
			@RequestBody(required = false) MediaRegisterRequest request) {
		return ApiResponse.of(SuccessCode.OK, shortsService.registerMedia(name, request, adminId));
	}

	@PostMapping("/sources/from-url")
	public ApiResponse<JsonNode> createSourceFromUrl(
			@AuthenticationPrincipal Long adminId, @Valid @RequestBody SourceFromUrlRequest request) {
		return ApiResponse.of(SuccessCode.OK, shortsService.createSourceFromUrl(request, adminId));
	}

	@PostMapping("/sources/{sourceId}/chunks")
	public ApiResponse<JsonNode> createChunk(
			@PathVariable long sourceId, @Valid @RequestBody ChunkCreateRequest request) {
		return ApiResponse.of(SuccessCode.OK, shortsService.createChunk(sourceId, request));
	}

	@PostMapping("/chunks/{chunkId}/stt")
	public ApiResponse<JsonNode> runStt(@PathVariable long chunkId, @RequestBody(required = false) SttRequest request) {
		return ApiResponse.of(SuccessCode.OK,
			shortsService.runStt(chunkId, request == null ? new SttRequest(null, null, null, false) : request));
	}

	@PostMapping("/chunks/{chunkId}/segment")
	public ApiResponse<JsonNode> runSegment(
			@PathVariable long chunkId, @RequestParam(defaultValue = "false") boolean force) {
		return ApiResponse.of(SuccessCode.OK, shortsService.runSegment(chunkId, force));
	}

	@PostMapping("/sources/{sourceId}/rank")
	public ApiResponse<JsonNode> runRank(
			@AuthenticationPrincipal Long adminId,
			@PathVariable long sourceId,
			@RequestBody(required = false) RankRequest request) {
		return ApiResponse.of(SuccessCode.OK, shortsService.runRank(sourceId, request, adminId));
	}

	@PostMapping("/sources/{sourceId}/pipeline")
	public ApiResponse<JsonNode> runPipeline(
			@AuthenticationPrincipal Long adminId,
			@PathVariable long sourceId,
			@RequestParam(defaultValue = "false") boolean resegment,
			@RequestBody(required = false) RankRequest request) {
		return ApiResponse.of(SuccessCode.OK,
			shortsService.runPipeline(sourceId, request, resegment, adminId));
	}

	@GetMapping("/cost")
	public ApiResponse<JsonNode> cost() {
		return ApiResponse.of(SuccessCode.OK, shortsService.totalCost());
	}

	@PostMapping("/runs/{runId}/segments/{segmentId}/cut")
	public ApiResponse<JsonNode> runCut(
			@PathVariable long runId,
			@PathVariable long segmentId,
			@RequestParam(defaultValue = "false") boolean replace) {
		return ApiResponse.of(SuccessCode.OK, shortsService.runCut(runId, segmentId, replace));
	}

	@GetMapping("/segments/{segmentId}/preview")
	public ResponseEntity<Resource> segmentPreview(@PathVariable long segmentId) {
		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("video/mp4"))
			.body(shortsService.downloadSegmentPreview(segmentId));
	}

	@PostMapping("/clips/{clipId}/render")
	public ApiResponse<JsonNode> renderClip(
			@PathVariable long clipId, @RequestParam(defaultValue = "false") boolean force) {
		return ApiResponse.of(SuccessCode.OK, shortsService.renderClip(clipId, force));
	}

	@PostMapping("/clips/{clipId}/review")
	public ResponseEntity<ApiResponse<JsonNode>> reviewClip(
			@AuthenticationPrincipal Long adminId,
			@PathVariable long clipId,
			@Valid @RequestBody ClipReviewRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(SuccessCode.CREATED, shortsService.reviewClip(clipId, request, adminId)));
	}

	@GetMapping("/clips/{clipId}/file")
	public ResponseEntity<Resource> downloadClip(@PathVariable long clipId) {
		return ResponseEntity.ok()
			.contentType(MediaType.valueOf("video/mp4"))
			.body(shortsService.downloadClip(clipId));
	}
}
