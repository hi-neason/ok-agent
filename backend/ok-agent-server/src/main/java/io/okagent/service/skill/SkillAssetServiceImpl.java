package io.okagent.service.skill;

import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.repository.skill.SkillFileRepository;
import io.okagent.web.skill.SkillAssetResponse;
import io.okagent.web.skill.SkillFileContentResponse;
import io.okagent.web.skill.SkillFileResponse;
import io.okagent.web.skill.SkillFileUpdateRequest;
import io.okagent.web.skill.SkillMetadataRequest;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SkillAssetServiceImpl implements SkillAssetService {
    private final SkillAssetRepository repository;
    private final SkillFileRepository fileRepository;
    private final SkillArchiveParser archiveParser;

    public SkillAssetServiceImpl(
            SkillAssetRepository repository, SkillFileRepository fileRepository, SkillArchiveParser archiveParser) {
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.archiveParser = archiveParser;
    }

    @Override
    @Transactional
    public SkillAssetResponse importArchive(
            String archiveName,
            byte[] archive,
            String requestedName,
            String requestedDescription,
            String businessDomain,
            boolean overwrite) {
        if (businessDomain == null || businessDomain.isBlank() || businessDomain.length() > 64) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Business domain is required and must not exceed 64 characters");
        }
        var parsed = archiveParser.parse(archiveName, archive);
        var existing = repository.findBySkillKey(parsed.skillKey());
        if (existing.isPresent() && !overwrite) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A skill with the parsed name already exists");
        }
        var asset = existing.orElseGet(() -> new SkillAsset(
                UUID.randomUUID(),
                parsed.skillKey(),
                parsed.name(),
                parsed.description(),
                "latest",
                io.okagent.domain.skill.SkillSourceType.FILE_IMPORT,
                null,
                "SKILL.md",
                parsed.entryContent(),
                true));
        if (existing.isPresent()) {
            asset.clearFiles();
            repository.flush();
        }
        asset.replaceArchive(
                parsed.skillKey(),
                prefer(requestedName, parsed.name()),
                prefer(requestedDescription, parsed.description()),
                businessDomain.trim(),
                archiveName,
                parsed.sha256(),
                archive.length,
                parsed.entryContent(),
                parsed.files());
        return SkillAssetResponse.from(repository.save(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SkillAssetResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return repository.findAll(pageable).map(SkillAssetResponse::from);
    }

    @Override
    @Transactional
    public SkillAssetResponse updateMetadata(UUID id, SkillMetadataRequest request) {
        var asset = find(id);
        asset.updateMetadata(request.name(), request.description(), request.businessDomain());
        return SkillAssetResponse.from(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillFileResponse> listFiles(UUID id) {
        find(id);
        return fileRepository.findAllBySkillIdOrderByFilePath(id).stream()
                .map(SkillFileResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SkillFileContentResponse getFile(UUID id, String path) {
        find(id);
        var file = fileRepository
                .findBySkillIdAndFilePath(id, path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found"));
        var textMediaType = file.getMediaType().startsWith("text/")
                || file.getMediaType().equals("application/json")
                || file.getMediaType().equals("application/yaml");
        var content = textMediaType ? decodeUtf8(file.getContent()) : null;
        var previewable = content != null;
        return new SkillFileContentResponse(
                file.getFilePath(),
                file.getMediaType(),
                file.getFileSize(),
                previewable,
                content,
                file.getVersion(),
                file.getUpdatedAt());
    }

    @Override
    @Transactional
    public SkillFileContentResponse updateFile(UUID id, SkillFileUpdateRequest request) {
        var asset = find(id);
        var file = fileRepository
                .findBySkillIdAndFilePath(id, request.path())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found"));
        if (file.getVersion() != request.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill file was modified by another operation");
        }
        if (!isTextMediaType(file.getMediaType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Binary files are read-only");
        }
        if (request.path().equals("SKILL.md")) {
            var metadata = archiveParser.parseMetadata(request.content());
            if (repository.existsBySkillKeyAndIdNot(metadata.skillKey(), id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A skill with the updated name already exists");
            }
            asset.updateManifestMetadata(
                    metadata.skillKey(), metadata.name(), metadata.description(), request.content());
        }
        file.updateContent(request.content().getBytes(StandardCharsets.UTF_8));
        asset.markFileModified();
        fileRepository.flush();
        return getFile(id, request.path());
    }

    @Override
    @Transactional
    public SkillAssetResponse enabled(UUID id, boolean value) {
        var asset = find(id);
        asset.setEnabled(value);
        return SkillAssetResponse.from(asset);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private SkillAsset find(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill asset not found"));
    }

    private String prefer(String requested, String parsed) {
        return requested == null || requested.isBlank() ? parsed : requested.trim();
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            return null;
        }
    }

    private boolean isTextMediaType(String mediaType) {
        return mediaType.startsWith("text/")
                || mediaType.equals("application/json")
                || mediaType.equals("application/yaml");
    }
}
