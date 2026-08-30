package com.swyp.backend.shorts.service;

import tools.jackson.databind.JsonNode;
import com.swyp.backend.shorts.dto.ChunkCreateRequest;
import com.swyp.backend.shorts.dto.ClipReviewRequest;
import com.swyp.backend.shorts.dto.RankRequest;
import com.swyp.backend.shorts.dto.SourceCreateRequest;
import com.swyp.backend.shorts.dto.SttRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShortsService {

	private final ShortsClient client;

	public JsonNode health() {
		return client.get("/health");
	}

	public JsonNode listSources() {
		return client.get("/api/sources");
	}

	public JsonNode getSource(long sourceId) {
		return client.get("/api/sources/" + sourceId);
	}

	public JsonNode getUtterances(long chunkId) {
		return client.get("/api/chunks/" + chunkId + "/utterances");
	}

	public JsonNode listJobs() {
		return client.get("/api/jobs");
	}

	public JsonNode getJob(String jobId) {
		return client.get("/api/jobs/" + jobId);
	}

	public JsonNode createSource(SourceCreateRequest request, Long adminId) {
		Map<String, Object> body = new HashMap<>();
		body.put("path", request.path());
		body.put("title", request.title());
		body.put("contentType", request.contentType() == null ? "LECTURE" : request.contentType());
		body.put("origin", request.origin());
		body.put("context", request.context());
		return client.post("/api/sources", ShortsClient.withAdmin(body, adminId));
	}

	public JsonNode createChunk(long sourceId, ChunkCreateRequest request) {
		return client.post("/api/sources/" + sourceId + "/chunks",
			Map.of("startSec", request.startSec(), "endSec", request.endSec()));
	}

	public JsonNode runStt(long chunkId, SttRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("model", request.model());
		body.put("initialPrompt", request.initialPrompt());
		body.put("force", request.force());
		return client.post("/api/chunks/" + chunkId + "/stt", body);
	}

	public JsonNode runSegment(long chunkId, boolean force) {
		return client.post("/api/chunks/" + chunkId + "/segment?force=" + force, null);
	}

	public JsonNode runRank(long sourceId, RankRequest request, Long adminId) {
		Map<String, Object> body = new HashMap<>();
		body.put("criteria", request == null ? null : request.criteria());
		return client.post("/api/sources/" + sourceId + "/rank", ShortsClient.withAdmin(body, adminId));
	}

	public JsonNode runPipeline(long sourceId, RankRequest request, boolean resegment, Long adminId) {
		Map<String, Object> body = new HashMap<>();
		body.put("criteria", request == null ? null : request.criteria());
		return client.post("/api/sources/" + sourceId + "/pipeline?resegment=" + resegment,
			ShortsClient.withAdmin(body, adminId));
	}

	public JsonNode totalCost() {
		return client.get("/api/cost");
	}

	public JsonNode runCut(long runId, long segmentId, boolean replace) {
		return client.post(
			"/api/runs/" + runId + "/segments/" + segmentId + "/cut?replace=" + replace, null);
	}

	public Resource downloadSegmentPreview(long segmentId) {
		return client.download("/api/segments/" + segmentId + "/preview");
	}

	public JsonNode renderClip(long clipId, boolean force) {
		return client.post("/api/clips/" + clipId + "/render?force=" + force, null);
	}

	public JsonNode reviewClip(long clipId, ClipReviewRequest request, Long adminId) {
		Map<String, Object> body = new HashMap<>();
		body.put("verdict", request.verdict());
		body.put("note", request.note());
		return client.post("/api/clips/" + clipId + "/review", ShortsClient.withAdmin(body, adminId));
	}

	public Resource downloadClip(long clipId) {
		return client.download("/api/clips/" + clipId + "/file");
	}
}
