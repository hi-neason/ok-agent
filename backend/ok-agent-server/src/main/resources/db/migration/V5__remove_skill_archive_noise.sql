DELETE FROM skill_file
WHERE file_path LIKE '__MACOSX/%'
   OR file_path LIKE '._%'
   OR file_path LIKE '%/._%'
   OR file_path = '.DS_Store'
   OR file_path LIKE '%/.DS_Store'
   OR file_path LIKE '__pycache__/%'
   OR file_path LIKE '%/__pycache__/%'
   OR file_path LIKE '%.pyc';
