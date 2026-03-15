package pms.whq.content;

import java.nio.file.Path;
import java.util.function.Consumer;

public class RuntimeContentService {

  private final RuntimeContentLoader loader;
  private final RuntimeContentValidator validator;

  public RuntimeContentService(Path projectRoot) {
    this.loader = new RuntimeContentLoader(projectRoot);
    this.validator = new RuntimeContentValidator();
  }

  public ContentRepository load(Consumer<ContentIssue> issueConsumer) {
    ContentRepository repository = loader.load();
    validator.pruneInvalidTableEntries(repository, issueConsumer);
    return repository;
  }
}
