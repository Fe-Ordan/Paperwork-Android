package rocks.paperwork.android.fragments;


import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rocks.paperwork.android.R;
import rocks.paperwork.android.activities.NoteActivity;
import rocks.paperwork.android.adapters.NotebookAdapter.Notebook;
import rocks.paperwork.android.adapters.NotesAdapter;
import rocks.paperwork.android.adapters.NotesAdapter.Note;
import rocks.paperwork.android.data.DatabaseContract;
import rocks.paperwork.android.data.NoteDataSource;
import rocks.paperwork.android.interfaces.AsyncCallback;
import rocks.paperwork.android.sync.SyncAdapter;


public class NotesFragment extends Fragment implements AsyncCallback
{
    private NotesAdapter mNotesAdapter;
    private TextView emptyText;
    private SwipeRefreshLayout mSwipeContainer;
    private Notebook mNotebook;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        mNotesAdapter = new NotesAdapter(getActivity(), R.id.list_notebooks, new ArrayList<Note>());

        ListView notesList = (ListView) view.findViewById(R.id.list_notebooks);
        notesList.setAdapter(mNotesAdapter);

        FloatingActionButton addNote = (FloatingActionButton) view.findViewById(R.id.add_notebook);

        emptyText = (TextView) view.findViewById(R.id.empty);

        addNote.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mNotebook == null)
                {
                    showNotebookSelection();
                }
                else
                {
                    createNewNote(mNotebook);
                }
            }
        });

        notesList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
            {
                viewNote(mNotesAdapter.getItem(position), false);
            }
        });

        notesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l)
            {
                showNoteDialog(i);
                return true;
            }
        });

        mSwipeContainer = (SwipeRefreshLayout) view.findViewById(R.id.swipeContainer);

        // Setup refresh listener which triggers new data loading
        mSwipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                SyncAdapter.syncImmediately(getActivity(), mSwipeContainer);
            }
        });

        Bundle bundle = getArguments();
        if (bundle != null)
        {
            if (bundle.containsKey("Notebook"))
            {
                mNotebook = (Notebook) bundle.getSerializable("Notebook");
            }
        }

        getActivity().getContentResolver().registerContentObserver(
                DatabaseContract.NoteEntry.CONTENT_URI, true, new ContentObserver(new Handler(getActivity().getMainLooper()))
                {
                    @Override
                    public void onChange(boolean selfChange)
                    {
                        updateView();
                    }
                });

        updateView();

        return view;
    }

    @Override
    public void updateView()
    {
        mSwipeContainer.setRefreshing(false);
        mNotesAdapter.clear();

        NoteDataSource noteDataSource = NoteDataSource.getInstance(getActivity());
        List<Note> notes;

        if (mNotebook != null)
        {
            notes = noteDataSource.getAllNotesFromNotebook(mNotebook);
        }
        else
        {
            notes = noteDataSource.getNotes(null);
        }

        mNotesAdapter.addAll(notes);

        if (mNotesAdapter.isEmpty())
        {
            emptyText.setVisibility(View.VISIBLE);
        }
        else
        {
            emptyText.setVisibility(View.GONE);
        }
    }

    public void showSearchResults(List<Note> notes)
    {
        mNotesAdapter.clear();
        mNotesAdapter.addAll(notes);
    }

    private void showNotebookSelection()
    {
        NoteDataSource noteDataSource = NoteDataSource.getInstance(getActivity());
        final List<Notebook> allNotebooks = noteDataSource.getAllNotebooks();
        CharSequence[] notebookChars = new CharSequence[allNotebooks.size()];

        for (int i = 0; i < allNotebooks.size(); i++)
        {
            notebookChars[i] = allNotebooks.get(i).getTitle();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select a notebook").setIcon(R.mipmap.ic_notebook_grey)
                .setItems(notebookChars, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        createNewNote(allNotebooks.get(which));
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void createNewNote(Notebook notebook)
    {
        Intent editNoteIntent = new Intent(getActivity(), NoteActivity.class);
        editNoteIntent.putExtra("NotebookId", notebook.getId());
        editNoteIntent.putExtra("EditMode", true);
        startActivity(editNoteIntent);
    }

    private void viewNote(Note note, boolean editMode)
    {
        Intent viewNoteIntent = new Intent(getActivity(), NoteActivity.class);
        viewNoteIntent.putExtra("NOTES", note);
        viewNoteIntent.putExtra("EditMode", editMode);
        startActivity(viewNoteIntent);
    }

    private void showNoteDialog(int position)
    {
        final Note note = mNotesAdapter.getItem(position);
        CharSequence[] options = {
                getString(R.string.edit),
                getString(R.string.share),
                getString(R.string.add_tag),
                getString(R.string.delete)};

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(options, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                switch (which)
                {
                    case 0: // edit
                        viewNote(note, true);
                        break;
                    case 1: // share
                        // TODO implement share
                        break;
                    case 2: // add a tag
                        // TODO implement add tags
                        break;
                    case 3: // delete note
                        showDeleteNoteDialog(note);
                        break;
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showDeleteNoteDialog(final Note note)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.delete) + ": " + note.getTitle())
                .setMessage(R.string.delete_note_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        note.setSyncStatus(DatabaseContract.NoteEntry.NOTE_STATUS.deleted);
                        NoteDataSource.getInstance(getActivity()).insertNote(note);
                        SyncAdapter.syncImmediately(getActivity());
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}